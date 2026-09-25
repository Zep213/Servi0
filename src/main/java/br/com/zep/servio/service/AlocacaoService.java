package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RecursoNaoEncontradoException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
        exigirVisivel(pastoralId, idOuZero(entidade));
        exigirGestorPastoral(pastoralId);

        Long id = idOuZero(entidade);
        Long vagaId = entidade.getVaga().getId();
        Long usuarioId = entidade.getUsuario().getId();

        if (repository.existsByVagaIdAndUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                vagaId, usuarioId, StatusConvite.OCUPANTES, id)) {
            throw new ConflitoException("Usuário já alocado nesta vaga");
        }

        List<String> impedimentos = elegibilidadeService.impedimentos(entidade.getUsuario(), entidade.getVaga(), id);
        if (!impedimentos.isEmpty()) {
            throw new RegraNegocioException(String.join("; ", impedimentos));
        }

        Integer quantidade = entidade.getVaga().getQuantidade();
        if (quantidade == null) {
            throw new RegraNegocioException("Defina a quantidade da vaga primeiro");
        }
        long ocupantes = repository.countByVagaIdAndActiveTrueAndStatusInAndIdNot(vagaId, StatusConvite.OCUPANTES, id);
        if (ocupantes >= quantidade) {
            throw new RegraNegocioException("Vaga já está com todas as posições preenchidas");
        }

        // Numa criação (id nulo) o convite sempre começa do zero; numa edição, o
        // atualizar() abaixo decide se a escalação de fato mudou antes de reiniciar o prazo.
        if (entidade.getId() == null) {
            reiniciarConvite(entidade, pastoralId);
        }
    }

    private void reiniciarConvite(Alocacao entidade, Long pastoralId) {
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
        Pastoral pastoralAnterior = vagaAnterior.getFuncao().getPastoral();
        boolean mudouEscalacao = !vagaAnterior.getId().equals(request.vagaId())
                || !usuarioAnterior.getId().equals(request.usuarioId());

        // 1.1: exige gestão tanto na pastoral de origem quanto na de destino (validar()
        // abaixo checa a de destino, já com a vaga/usuário novos aplicados).
        exigirVisivel(pastoralAnterior.getId(), id);
        exigirGestorPastoral(pastoralAnterior.getId());

        atualizarEntidade(request, entidade);
        validar(entidade);
        if (mudouEscalacao) {
            Long pastoralNovaId = entidade.getVaga().getFuncao().getPastoral().getId();
            reiniciarConvite(entidade, pastoralNovaId);
        }
        Alocacao salva = repository.save(entidade);

        boolean isVice = pastoraisPermissao.temPapel(pastoralAnterior.getId(), PapelPastoral.VICE.name());
        boolean isCoordenador = pastoraisPermissao.temPapel(pastoralAnterior.getId(), PapelPastoral.COORDENADOR.name());
        if (isVice && !isCoordenador) {
            Usuario autor = referencia(usuarioRepository, usuarioId(), "Usuario");
            alteracaoPendenteService.registrar(salva, pastoralAnterior, autor, vagaAnterior, usuarioAnterior);
        }

        return paraResposta(salva);
    }

    /** Escala é gerida por quem cadastra pessoas nela: só COORDENADOR ou VICE da pastoral. */
    @Override
    @Transactional
    public void desativar(Long id) {
        Alocacao entidade = obterAtivo(id);
        Long pastoralId = entidade.getVaga().getFuncao().getPastoral().getId();
        exigirVisivel(pastoralId, id);
        exigirGestorPastoral(pastoralId);
        entidade.setActive(false);
        repository.save(entidade);
    }

    /** Parte 4: alocações visíveis a quem não é PADRE/ADMIN são só as de funções de pastorais do usuário. */
    @Override
    @Transactional(readOnly = true)
    public Page<AlocacaoResponseDTO> listar(Pageable pageable) {
        Optional<List<Long>> visiveis = pastoraisPermissao.pastoraisVisiveis();
        if (visiveis.isEmpty()) {
            return super.listar(pageable);
        }
        if (visiveis.get().isEmpty()) {
            return Page.empty(pageable);
        }
        return repository.findByParoquiaIdAndVagaFuncaoPastoralIdInAndActiveTrue(paroquiaId(), visiveis.get(), pageable)
                .map(this::paraResposta);
    }

    @Override
    @Transactional(readOnly = true)
    public AlocacaoResponseDTO buscar(Long id) {
        Alocacao entidade = obterAtivo(id);
        exigirVisivel(entidade.getVaga().getFuncao().getPastoral().getId(), id);
        return paraResposta(entidade);
    }

    /** Pastoral fora do alcance do usuário (Parte 4): 404, pra não revelar nem a existência do recurso. */
    private void exigirVisivel(Long pastoralId, Long id) {
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException(nomeRecurso(), id);
        }
    }

    private void exigirGestorPastoral(Long pastoralId) {
        if (!pastoraisPermissao.temQualquerPapel(pastoralId, PapelPastoral.COORDENADOR.name(), PapelPastoral.VICE.name())) {
            throw new AccessDeniedException("Só o coordenador ou vice da pastoral gerencia a escala");
        }
    }

    /**
     * O convidado aceita ou recusa a própria escalação, dentro do prazo configurado pela pastoral.
     * noRollbackFor: a expiração gravada abaixo tem que valer mesmo a chamada terminando em erro.
     */
    @Transactional(noRollbackFor = RegraNegocioException.class)
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
