package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.mapper.UsuarioFuncaoMapper;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioFuncao;
import br.com.zep.servio.model.dto.UsuarioFuncaoRequestDTO;
import br.com.zep.servio.model.dto.UsuarioFuncaoResponseDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioFuncaoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Habilidades das pessoas (as funções que cada uma sabe fazer). Quem marca e desmarca é o coordenador da
 * pastoral dona da função (ou PADRE/ADMIN, que valem como coordenador), e só para quem tem papel nessa pastoral.
 */
@Service
@RequiredArgsConstructor
public class UsuarioFuncaoService extends CrudService<UsuarioFuncao, UsuarioFuncaoRequestDTO, UsuarioFuncaoResponseDTO> {

    private final UsuarioFuncaoRepository repository;
    private final UsuarioFuncaoMapper mapper;
    private final UsuarioRepository usuarioRepository;
    private final FuncaoRepository funcaoRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final AuditLogService auditLogService;

    @Override
    protected TenantRepository<UsuarioFuncao> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "UsuarioFuncao";
    }

    @Override
    protected UsuarioFuncaoResponseDTO paraResposta(UsuarioFuncao entity) {
        return mapper.toResponse(entity);
    }

    /**
     * Permissão (coordenador da pastoral da função), pessoa da pastoral e unicidade **entre as ativas**: depois de
     * desmarcar uma habilidade dá para marcar de novo (o índice parcial do banco já permitia; este serviço é que
     * barrava).
     */
    @Override
    protected void validar(UsuarioFuncao entidade) {
        Long pastoralId = entidade.getFuncao().getPastoral().getId();
        exigirGestor(pastoralId);
        if (usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(entidade.getUsuario().getId(), pastoralId)
                .isEmpty()) {
            throw new RegraNegocioException("Esta pessoa não faz parte da pastoral desta função");
        }
        if (repository.existsByUsuarioIdAndFuncaoIdAndActiveTrueAndIdNot(
                entidade.getUsuario().getId(), entidade.getFuncao().getId(), idOuZero(entidade))) {
            throw new ConflitoException("Usuário já possui esta função");
        }
    }

    @Override
    protected UsuarioFuncao paraEntidade(UsuarioFuncaoRequestDTO request) {
        UsuarioFuncao entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(UsuarioFuncaoRequestDTO request, UsuarioFuncao entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    @Override
    @Transactional
    public UsuarioFuncaoResponseDTO criar(UsuarioFuncaoRequestDTO request) {
        UsuarioFuncaoResponseDTO criada = super.criar(request);
        registrar("marcada", criada.id(), criada.usuarioId(), criada.funcaoId());
        return criada;
    }

    /** Trocar a função de uma habilidade mexe nas duas pastorais: o coordenador precisa valer para a antiga também. */
    @Override
    @Transactional
    public UsuarioFuncaoResponseDTO atualizar(Long id, UsuarioFuncaoRequestDTO request) {
        exigirGestor(obterAtivo(id).getFuncao().getPastoral().getId());
        UsuarioFuncaoResponseDTO atualizada = super.atualizar(id, request);
        registrar("alterada", atualizada.id(), atualizada.usuarioId(), atualizada.funcaoId());
        return atualizada;
    }

    @Override
    @Transactional
    public void desativar(Long id) {
        UsuarioFuncao habilidade = obterAtivo(id);
        exigirGestor(habilidade.getFuncao().getPastoral().getId());
        super.desativar(id);
        registrar("desmarcada", id, habilidade.getUsuario().getId(), habilidade.getFuncao().getId());
    }

    /** Mesma regra de gestão de papéis (UsuarioPastoralService): pastoral fora do alcance 404, sem ser coordenador 403. */
    public void exigirGestor(Long pastoralId) {
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new ServioException("Você não pode alterar nada em outra pastoral", HttpStatus.NOT_FOUND);
        }
        if (!pastoraisPermissao.temPapel(pastoralId, PapelPastoral.COORDENADOR.name())) {
            throw new AccessDeniedException("Só o coordenador desta pastoral (ou padre/ADMIN) gerencia habilidades nela");
        }
    }

    /** Auditoria sem e-mail nem nome: só ids de habilidade, pessoa e função. */
    void registrar(String acao, Long habilidadeId, Long usuarioId, Long funcaoId) {
        Usuario autor = usuarioRepository.findById(usuarioId()).orElseThrow();
        auditLogService.registrar("HABILIDADE_ALTERADA", habilidadeId, autor, paroquiaId(),
                "Habilidade " + acao + ": usuário " + usuarioId + ", função " + funcaoId);
    }

    private void resolverRelacoes(UsuarioFuncaoRequestDTO request, UsuarioFuncao entity) {
        entity.setUsuario(referencia(usuarioRepository, request.usuarioId(), "Usuario"));
        entity.setFuncao(referencia(funcaoRepository, request.funcaoId(), "Funcao"));
    }
}
