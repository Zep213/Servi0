package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.mapper.AlocacaoMapper;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.AlocacaoRequestDTO;
import br.com.zep.servio.model.dto.AlocacaoResponseDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import br.com.zep.servio.service.escalacao.ElegibilidadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlocacaoService extends CrudService<Alocacao, AlocacaoRequestDTO, AlocacaoResponseDTO> {

    private final AlocacaoRepository repository;
    private final AlocacaoMapper mapper;
    private final VagaRepository vagaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ElegibilidadeService elegibilidadeService;
    private final ConfiguracaoPastoralService configuracaoPastoralService;
    private final PastoraisPermissao pastoraisPermissao;
    private final AlteracaoPendenteService alteracaoPendenteService;

    @Override
    protected TenantRepository<Alocacao> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Alocacao";
    }

    @Override
    protected AlocacaoResponseDTO paraResposta(Alocacao entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected void validar(Alocacao entidade) {
        Long pastoralId = entidade.getVaga().getFuncao().getPastoral().getId();
        exigirGestorPastoral(pastoralId);

        if (repository.existsByVagaIdAndUsuarioId(entidade.getVaga().getId(), entidade.getUsuario().getId())) {
            throw new ConflitoException("Usuário já alocado nesta vaga");
        }

        List<String> impedimentos = elegibilidadeService.impedimentos(entidade.getUsuario(), entidade.getVaga());
        if (!impedimentos.isEmpty()) {
            throw new RegraNegocioException(String.join("; ", impedimentos));
        }

        long prazoHoras = configuracaoPastoralService.prazoRespostaHoras(pastoralId);
        entidade.setStatus(StatusConvite.PENDENTE);
        entidade.setDataLimiteResposta(LocalDateTime.now().plusHours(prazoHoras));
    }

    @Override
    protected Alocacao paraEntidade(AlocacaoRequestDTO request) {
        Alocacao entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(AlocacaoRequestDTO request, Alocacao entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(AlocacaoRequestDTO request, Alocacao entity) {
        entity.setVaga(referencia(vagaRepository, request.vagaId(), "Vaga"));
        entity.setUsuario(referencia(usuarioRepository, request.usuarioId(), "Usuario"));
    }

    /**
     * Quando quem edita é o vice da pastoral (não o coordenador), a mudança é aplicada
     * normalmente mas fica registrada como pendente até o coordenador confirmar ou desfazer.
     */
    @Override
    @Transactional
    public AlocacaoResponseDTO atualizar(Long id, AlocacaoRequestDTO request) {
        Alocacao entidade = obterAtivo(id);
        Vaga vagaAnterior = entidade.getVaga();
        Usuario usuarioAnterior = entidade.getUsuario();
        Pastoral pastoral = vagaAnterior.getFuncao().getPastoral();

        atualizarEntidade(request, entidade);
        validar(entidade);
        Alocacao salva = repository.save(entidade);

        boolean isVice = pastoraisPermissao.temPapel(pastoral.getId(), PapelPastoral.VICE.name());
        boolean isCoordenador = pastoraisPermissao.temPapel(pastoral.getId(), PapelPastoral.COORDENADOR.name());
        if (isVice && !isCoordenador) {
            Usuario autor = referencia(usuarioRepository, usuarioId(), "Usuario");
            alteracaoPendenteService.registrar(salva, pastoral, autor, vagaAnterior, usuarioAnterior);
        }

        return paraResposta(salva);
    }

    /** Escala é gerida por quem cadastra pessoas nela: só COORDENADOR ou VICE da pastoral. */
    @Override
    @Transactional
    public void desativar(Long id) {
        Alocacao entidade = obterAtivo(id);
        exigirGestorPastoral(entidade.getVaga().getFuncao().getPastoral().getId());
        entidade.setActive(false);
        repository.save(entidade);
    }

    private void exigirGestorPastoral(Long pastoralId) {
        if (!pastoraisPermissao.temQualquerPapel(pastoralId, PapelPastoral.COORDENADOR.name(), PapelPastoral.VICE.name())) {
            throw new AccessDeniedException("Só o coordenador ou vice da pastoral gerencia a escala");
        }
    }

    /** O convidado aceita ou recusa a própria escalação, dentro do prazo configurado pela pastoral. */
    @Transactional
    public AlocacaoResponseDTO responder(Long id, boolean aceitar) {
        Alocacao entidade = obterAtivo(id);
        if (!entidade.getUsuario().getId().equals(usuarioId())) {
            throw new ServioException("Você só pode responder ao próprio convite", HttpStatus.FORBIDDEN);
        }
        if (entidade.getStatus() != StatusConvite.PENDENTE) {
            throw new RegraNegocioException("Este convite já foi respondido");
        }
        if (entidade.getDataLimiteResposta() != null && LocalDateTime.now().isAfter(entidade.getDataLimiteResposta())) {
            entidade.setStatus(StatusConvite.EXPIRADA);
            repository.save(entidade);
            throw new RegraNegocioException("Prazo de resposta deste convite expirou");
        }

        entidade.setStatus(aceitar ? StatusConvite.ACEITA : StatusConvite.RECUSADA);
        return paraResposta(repository.save(entidade));
    }
}
