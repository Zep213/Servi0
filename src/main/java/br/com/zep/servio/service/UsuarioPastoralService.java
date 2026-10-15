package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.mapper.UsuarioPastoralMapper;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.dto.UsuarioPastoralRequestDTO;
import br.com.zep.servio.model.dto.UsuarioPastoralResponseDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.SessaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/**
 * Quem tem papel dentro de uma pastoral é decisão do COORDENADOR daquela pastoral
 * (ou do ADMIN global, para poder inicializar a hierarquia de uma pastoral nova).
 */
@Service
@RequiredArgsConstructor
public class UsuarioPastoralService extends CrudService<UsuarioPastoral, UsuarioPastoralRequestDTO, UsuarioPastoralResponseDTO> {

    private static final int MAX_SECRETARIOS_POR_PASTORAL = 2;
    private static final Map<PapelPastoral, String> CARGOS_UNICOS = Map.of(
            PapelPastoral.TECNICO, "um técnico de TI",
            PapelPastoral.REDES_SOCIAIS, "uma pessoa nas redes sociais");

    private final UsuarioPastoralRepository repository;
    private final UsuarioPastoralMapper mapper;
    private final UsuarioRepository usuarioRepository;
    private final PastoralRepository pastoralRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final SessaoService sessaoService;

    @Override
    protected TenantRepository<UsuarioPastoral> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "UsuarioPastoral";
    }

    @Override
    protected UsuarioPastoralResponseDTO paraResposta(UsuarioPastoral entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected UsuarioPastoral paraEntidade(UsuarioPastoralRequestDTO request) {
        UsuarioPastoral entity = mapper.toEntity(request);
        entity.setPapel(request.papel());
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(UsuarioPastoralRequestDTO request, UsuarioPastoral entity) {
        mapper.updateEntity(request, entity);
        entity.setPapel(request.papel());
        resolverRelacoes(request, entity);
    }

    @Override
    protected void validar(UsuarioPastoral entidade) {
        Long pastoralId = entidade.getPastoral().getId();
        exigirGestor(pastoralId, entidade.getPapel());

        Long id = idOuZero(entidade);
        if (repository.existsByUsuarioIdAndPastoralIdAndActiveTrueAndIdNot(
                entidade.getUsuario().getId(), pastoralId, id)) {
            throw new ConflitoException("Usuário já tem um papel nesta pastoral");
        }
        if (entidade.getPapel() == PapelPastoral.SECRETARIO
                && repository.countByPastoralIdAndPapelAndActiveTrueAndIdNot(pastoralId, PapelPastoral.SECRETARIO, id)
                    >= MAX_SECRETARIOS_POR_PASTORAL) {
            throw new RegraNegocioException("Esta pastoral já tem o máximo de " + MAX_SECRETARIOS_POR_PASTORAL + " secretários");
        }
        if (CARGOS_UNICOS.containsKey(entidade.getPapel())
                && repository.countByPastoralIdAndPapelAndActiveTrueAndIdNot(pastoralId, entidade.getPapel(), id) >= 1) {
            throw new RegraNegocioException("Esta pastoral já tem " + CARGOS_UNICOS.get(entidade.getPapel()));
        }
    }

    /**
     * Quem ganha papel passa a ter outra duração de sessão (60 dias só de membro, 7 com papel de gestão, decidido no
     * login): criar, trocar ou tirar um papel derruba as sessões da pessoa afetada, para ela entrar de novo com a
     * duração certa. Só depois do commit: se a alteração for recusada, ninguém é deslogado.
     */
    @Override
    @Transactional
    public UsuarioPastoralResponseDTO criar(UsuarioPastoralRequestDTO request) {
        UsuarioPastoralResponseDTO resposta = super.criar(request);
        encerrarSessoesDe(usuarioRepository.findById(request.usuarioId()).map(Usuario::getEmail).orElse(null));
        return resposta;
    }

    /** A pastoral de uma participação é imutável: só o papel muda; mover para outra pastoral é 422. */
    @Override
    @Transactional
    public UsuarioPastoralResponseDTO atualizar(Long id, UsuarioPastoralRequestDTO request) {
        UsuarioPastoral entidade = obterAtivo(id);
        if (!entidade.getPastoral().getId().equals(request.pastoralId())) {
            throw new RegraNegocioException("A pastoral desta participação não pode ser alterada");
        }
        Long usuarioAntigo = entidade.getUsuario().getId();
        String emailAntigo = entidade.getUsuario().getEmail();
        PapelPastoral papelAntigo = entidade.getPapel();
        atualizarEntidade(request, entidade);
        validar(entidade);
        UsuarioPastoralResponseDTO resposta = paraResposta(repository.save(entidade));
        // salvar o mesmo papel para a mesma pessoa não muda nada: não derruba sessão
        if (papelAntigo != entidade.getPapel() || !usuarioAntigo.equals(entidade.getUsuario().getId())) {
            encerrarSessoesDe(emailAntigo);
            encerrarSessoesDe(entidade.getUsuario().getEmail());
        }
        return resposta;
    }

    /** Atribuir/remover papel é decisão de gestão da pastoral: só o coordenador dela, o padre ou o ADMIN. */
    @Override
    @Transactional
    public void desativar(Long id) {
        UsuarioPastoral entidade = obterAtivo(id);
        exigirGestor(entidade.getPastoral().getId(), entidade.getPapel());
        entidade.setActive(false);
        repository.save(entidade);
        encerrarSessoesDe(entidade.getUsuario().getEmail());
    }

    private void encerrarSessoesDe(String email) {
        if (email != null) {
            sessaoService.encerrarTodasAposCommit(email);
        }
    }

    /**
     * Só padre ou ADMIN atribuem o papel COORDENADOR (gate de Perfil puro, vale pra qualquer
     * pastoral). Os demais papéis, o coordenador da pastoral também pode — mas pastoral fora
     * do alcance do usuário (Parte 4) dá 404 em vez de 403 (não revela a existência da
     * pastoral). Decisão do usuário (Parte 6, item 4): mantém o status 404, mas com uma
     * mensagem explícita em vez do "não encontrado" genérico — só quem já não é padre/ADMIN
     * cai aqui (eles sempre passam em {@code visivel()}).
     */
    private void exigirGestor(Long pastoralId, PapelPastoral papelAlvo) {
        if (papelAlvo == PapelPastoral.COORDENADOR) {
            if (!pastoraisPermissao.ehAdmin() && !pastoraisPermissao.ehPadre()) {
                throw new AccessDeniedException("Só o padre ou o ADMIN atribuem o papel de coordenador");
            }
            return;
        }
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new ServioException("Você não pode alterar nada em outra pastoral", HttpStatus.NOT_FOUND);
        }
        if (!pastoraisPermissao.temPapel(pastoralId, PapelPastoral.COORDENADOR.name())) {
            throw new AccessDeniedException("Só o coordenador desta pastoral (ou padre/ADMIN) atribui papéis nela");
        }
    }

    private void resolverRelacoes(UsuarioPastoralRequestDTO request, UsuarioPastoral entity) {
        entity.setUsuario(referencia(usuarioRepository, request.usuarioId(), "Usuario"));
        entity.setPastoral(referencia(pastoralRepository, request.pastoralId(), "Pastoral"));
    }
}
