package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.mapper.AlocacaoMapper;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.AlocacaoResponseDTO;
import br.com.zep.servio.model.dto.CandidatoDTO;
import br.com.zep.servio.model.dto.ReferenciaDTO;
import br.com.zep.servio.model.dto.ResultadoSorteioDTO;
import br.com.zep.servio.model.dto.VagaIncompletaDTO;
import br.com.zep.servio.model.enumerated.OrigemAlocacao;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.AlteracaoPendenteService;
import br.com.zep.servio.service.escalacao.evento.AlocacaoSubstituidaEvent;
import br.com.zep.servio.service.escalacao.evento.VagaSemElegiveisEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fachada da escalação: sortear, escalar à mão, substituir e reenviar convite. Cada operação
 * roda numa transação. Toda alteração de ocupação trava a vaga antes de contar os ocupantes
 * (ver VagaRepository.findByIdParaEscalarComLock), para duas operações concorrentes não
 * passarem da quantidade.
 */
@Service
@RequiredArgsConstructor
public class EscalacaoService {

    private static final String SORTEIO_SO_EM_MISSA = "Festividades são escaladas pelo coordenador";
    /** Quem recusou ou deixou expirar o convite desta vaga não volta no sorteio automático (pode ser escalado à mão). */
    private static final Set<StatusConvite> RECUSARAM = Set.of(StatusConvite.RECUSADA, StatusConvite.EXPIRADA);

    private final VagaRepository vagaRepository;
    private final AlocacaoRepository alocacaoRepository;
    private final CelebracaoRepository celebracaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final ElegibilidadeService elegibilidadeService;
    private final SorteioService sorteioService;
    private final ConviteService conviteService;
    private final AlteracaoPendenteService alteracaoPendenteService;
    private final AlocacaoMapper alocacaoMapper;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    /** Sorteia as vagas da pastoral numa celebração de missa dominical. Vagas sem quantidade ficam de fora. */
    @Transactional
    public ResultadoSorteioDTO sortearCelebracao(Long pastoralId, Long celebracaoId) {
        exigirGestor(pastoralId, "Celebracao", celebracaoId, false);
        Celebracao celebracao = celebracaoRepository.findByIdAndParoquiaIdAndActiveTrue(celebracaoId, paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Celebracao", celebracaoId));
        exigirMissa(celebracao);

        List<Long> vagaIds = vagaRepository.findByCelebracaoIdAndActiveTrue(celebracaoId).stream()
                .filter(v -> v.getFuncao().getPastoral().getId().equals(pastoralId))
                .filter(v -> v.getQuantidade() != null)
                .map(Vaga::getId)
                .sorted()
                .toList();

        List<Vaga> vagas = new ArrayList<>();
        for (Long vagaId : vagaIds) {
            vagas.add(travar(vagaId));
        }
        return sortear(vagas);
    }

    /** Sorteia uma vaga específica (ou completa o que faltar nela). */
    @Transactional
    public ResultadoSorteioDTO sortearVaga(Long vagaId) {
        Vaga vaga = travar(vagaId);
        exigirGestor(vaga.getFuncao().getPastoral().getId(), "Vaga", vagaId, false);
        exigirMissa(vaga.getCelebracao());
        if (vaga.getQuantidade() == null) {
            throw new RegraNegocioException("Defina a quantidade da vaga primeiro");
        }
        return sortear(List.of(vaga));
    }

    /** Lista de membros com o motivo de cada impedimento, para o coordenador escolher. */
    @Transactional(readOnly = true)
    public List<CandidatoDTO> candidatos(Long vagaId) {
        Vaga vaga = vagaRepository.findByIdAndParoquiaIdAndActiveTrue(vagaId, paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));
        exigirGestor(vaga.getFuncao().getPastoral().getId(), "Vaga", vagaId, false);
        return elegibilidadeService.avaliar(vaga).stream()
                .map(c -> new CandidatoDTO(new ReferenciaDTO(c.usuario().getId(), c.usuario().getNome()),
                        c.elegivel(), c.motivos(), c.ultimaVezQueServiu()))
                .toList();
    }

    /**
     * Escala uma pessoa numa vaga à mão. Sem forçar, quem tem impedimento não entra. Forçar é
     * só do coordenador (ou padre/admin, que valem como coordenador). Quem está em outra
     * pastoral fica de fora pelo 404 da visibilidade.
     */
    @Transactional
    public AlocacaoResponseDTO escalar(Long vagaId, Long usuarioId, boolean forcar) {
        Vaga vaga = travar(vagaId);
        exigirGestor(vaga.getFuncao().getPastoral().getId(), "Vaga", vagaId, forcar);
        Usuario usuario = obterUsuario(usuarioId);

        exigirNaoAlocado(vagaId, usuarioId, 0L);
        if (!forcar) {
            List<String> impedimentos = elegibilidadeService.impedimentos(usuario, vaga, 0L);
            if (!impedimentos.isEmpty()) {
                throw new RegraNegocioException(String.join("; ", impedimentos));
            }
        }
        if (vaga.getQuantidade() == null) {
            throw new RegraNegocioException("Defina a quantidade da vaga primeiro");
        }
        exigirPosicaoLivre(vaga, 0L);

        return resposta(novaAlocacao(vaga, usuario, OrigemAlocacao.COORDENADOR));
    }

    /**
     * Troca quem ocupa uma alocação. A anterior vira SUBSTITUIDA e perde o token; a nova nasce
     * PENDENTE com o prazo normal. Feita pelo vice, fica registrada como alteração pendente
     * para o coordenador confirmar ou desfazer.
     */
    @Transactional
    public AlocacaoResponseDTO substituir(Long alocacaoId, Long novoUsuarioId, boolean forcar) {
        Alocacao atual = alocacaoRepository.findByIdAndParoquiaIdAndActiveTrue(alocacaoId, paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alocacao", alocacaoId));
        Vaga vaga = travar(atual.getVaga().getId());
        Pastoral pastoral = vaga.getFuncao().getPastoral();
        exigirGestor(pastoral.getId(), "Alocacao", alocacaoId, forcar);

        if (!StatusConvite.OCUPANTES.contains(atual.getStatus())) {
            throw new RegraNegocioException("Só é possível substituir quem está ocupando a vaga");
        }
        Usuario anterior = atual.getUsuario();
        Usuario novo = obterUsuario(novoUsuarioId);
        if (novo.getId().equals(anterior.getId())) {
            throw new ConflitoException("Esta pessoa já está nesta vaga");
        }
        exigirNaoAlocado(vaga.getId(), novoUsuarioId, atual.getId());
        if (!forcar) {
            List<String> impedimentos = elegibilidadeService.impedimentos(novo, vaga, atual.getId());
            if (!impedimentos.isEmpty()) {
                throw new RegraNegocioException(String.join("; ", impedimentos));
            }
        }
        exigirPosicaoLivre(vaga, atual.getId());

        Usuario autor = usuarioLogadoReferencia();
        atual.setStatus(StatusConvite.SUBSTITUIDA);
        atual.setSubstituidaEm(LocalDateTime.now(clock));
        atual.setTokenHash(null);
        alocacaoRepository.save(atual);

        Alocacao nova = novaAlocacao(vaga, novo, OrigemAlocacao.COORDENADOR);
        eventos.publishEvent(new AlocacaoSubstituidaEvent(atual.getId(), nova.getId()));

        boolean viceSemCoordenacao = pastoraisPermissao.temPapel(pastoral.getId(), PapelPastoral.VICE.name())
                && !pastoraisPermissao.temPapel(pastoral.getId(), PapelPastoral.COORDENADOR.name());
        if (viceSemCoordenacao) {
            alteracaoPendenteService.registrar(nova, pastoral, autor, vaga, anterior);
        }
        return resposta(nova);
    }

    /** Reenvia o convite pendente com token novo e o mesmo prazo. No máximo uma vez por hora. */
    @Transactional
    public AlocacaoResponseDTO reenviarConvite(Long alocacaoId) {
        Alocacao alocacao = alocacaoRepository.findByIdAndParoquiaIdAndActiveTrue(alocacaoId, paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Alocacao", alocacaoId));
        Vaga vaga = travar(alocacao.getVaga().getId());
        exigirGestor(vaga.getFuncao().getPastoral().getId(), "Alocacao", alocacaoId, false);
        if (alocacao.getStatus() != StatusConvite.PENDENTE) {
            throw new RegraNegocioException("Só é possível reenviar convite que está pendente");
        }
        conviteService.reenviar(alocacao);
        return resposta(alocacao);
    }

    // ---------------------------------------------------------------- sorteio

    private ResultadoSorteioDTO sortear(List<Vaga> vagas) {
        List<AlocacaoResponseDTO> convidados = new ArrayList<>();
        List<VagaIncompletaDTO> incompletas = new ArrayList<>();
        Map<Long, Set<Long>> membrosPorPastoral = new HashMap<>();

        for (Vaga vaga : vagas) {
            int faltam = vaga.getQuantidade() - (int) ocupantes(vaga.getId(), 0L);
            if (faltam <= 0) {
                continue;
            }
            Set<Long> recusaram = alocacaoRepository.findByVagaIdAndActiveTrue(vaga.getId()).stream()
                    .filter(a -> RECUSARAM.contains(a.getStatus()))
                    .map(a -> a.getUsuario().getId())
                    .collect(Collectors.toSet());
            Set<Long> sorteaveis = membrosPorPastoral.computeIfAbsent(
                    vaga.getFuncao().getPastoral().getId(), pastoralId -> membrosSorteaveis(pastoralId, vaga.getParoquiaId()));
            List<CandidatoAvaliado> candidatos = elegibilidadeService.avaliar(vaga).stream()
                    .filter(c -> sorteaveis.contains(c.usuario().getId()))
                    .filter(c -> !recusaram.contains(c.usuario().getId()))
                    .toList();
            List<Usuario> escolhidos = sorteioService.escolher(candidatos, faltam);
            for (Usuario escolhido : escolhidos) {
                convidados.add(resposta(novaAlocacao(vaga, escolhido, OrigemAlocacao.SORTEIO)));
            }
            int restam = faltam - escolhidos.size();
            if (restam > 0) {
                incompletas.add(new VagaIncompletaDTO(vaga.getId(), referencia(vaga), restam));
                eventos.publishEvent(new VagaSemElegiveisEvent(vaga.getId(), restam));
            }
        }
        return new ResultadoSorteioDTO(convidados, incompletas);
    }

    /**
     * Só MEMBRO é sorteado. Coordenação, secretaria, tesouraria, técnico e redes sociais servem
     * de outra forma; continuam podendo ser escalados à mão.
     */
    private Set<Long> membrosSorteaveis(Long pastoralId, Long paroquiaId) {
        return usuarioPastoralRepository.findUsuariosPorPapel(pastoralId, paroquiaId, PapelPastoral.MEMBRO).stream()
                .map(Usuario::getId)
                .collect(Collectors.toSet());
    }

    // ---------------------------------------------------------------- regras

    /** Cria a alocação com origem, quem escalou, prazo e token. Quem chama faz as checagens antes. */
    private Alocacao novaAlocacao(Vaga vaga, Usuario usuario, OrigemAlocacao origem) {
        Alocacao alocacao = new Alocacao();
        alocacao.setParoquiaId(paroquiaId());
        alocacao.setVaga(vaga);
        alocacao.setUsuario(usuario);
        alocacao.setOrigem(origem);
        alocacao.setEscaladoPor(usuarioLogadoReferencia());
        alocacao.setStatus(StatusConvite.PENDENTE);
        alocacao.setDataLimiteResposta(conviteService.novoPrazo(vaga.getFuncao().getPastoral().getId()));
        alocacaoRepository.save(alocacao);
        conviteService.emitir(alocacao, TipoNotificacao.CONVITE);
        return alocacao;
    }

    /** Trava a vaga da paróquia do usuário; 404 se não existir ou for de outra paróquia. */
    private Vaga travar(Long vagaId) {
        return vagaRepository.findByIdParaEscalarComLock(vagaId, paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Vaga", vagaId));
    }

    /**
     * Pastoral fora do alcance dá 404 (não revela que existe); sem papel de gestão, 403; forçar
     * exige coordenação (padre e admin passam pelo papel COORDENADOR, como no resto do sistema).
     */
    private void exigirGestor(Long pastoralId, String recurso, Long id, boolean forcar) {
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException(recurso, id);
        }
        if (!pastoraisPermissao.temQualquerPapel(pastoralId, PapelPastoral.COORDENADOR.name(), PapelPastoral.VICE.name())) {
            throw new AccessDeniedException("Só o coordenador ou vice da pastoral gerencia a escala");
        }
        if (forcar && !pastoraisPermissao.temPapel(pastoralId, PapelPastoral.COORDENADOR.name())) {
            throw new AccessDeniedException("Só o coordenador pode escalar alguém com impedimento");
        }
    }

    private void exigirMissa(Celebracao celebracao) {
        if (celebracao.getTipo() != TipoCelebracao.MISSA_DOMINICAL) {
            throw new RegraNegocioException(SORTEIO_SO_EM_MISSA);
        }
    }

    private void exigirNaoAlocado(Long vagaId, Long usuarioId, Long ignorarAlocacaoId) {
        if (alocacaoRepository.existsByVagaIdAndUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                vagaId, usuarioId, StatusConvite.OCUPANTES, ignorarAlocacaoId)) {
            throw new ConflitoException("Usuário já alocado nesta vaga");
        }
    }

    private void exigirPosicaoLivre(Vaga vaga, Long ignorarAlocacaoId) {
        if (ocupantes(vaga.getId(), ignorarAlocacaoId) >= vaga.getQuantidade()) {
            throw new RegraNegocioException("Vaga já está com todas as posições preenchidas");
        }
    }

    private long ocupantes(Long vagaId, Long ignorarAlocacaoId) {
        return alocacaoRepository.countByVagaIdAndActiveTrueAndStatusInAndIdNot(
                vagaId, StatusConvite.OCUPANTES, ignorarAlocacaoId);
    }

    /**
     * Quem está logado, sem o filtro de paróquia: um ADMIN que assumiu outra paróquia não pertence
     * a ela, mas continua sendo quem escalou. Só a referência (FK), sem carregar nada.
     */
    private Usuario usuarioLogadoReferencia() {
        return usuarioRepository.getReferenceById(usuarioId());
    }

    private Usuario obterUsuario(Long usuarioId) {
        return usuarioRepository.findByIdAndParoquiaIdAndActiveTrue(usuarioId, paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuario", usuarioId));
    }

    private ReferenciaDTO referencia(Vaga vaga) {
        return new ReferenciaDTO(vaga.getFuncao().getId(), vaga.getFuncao().getNome());
    }

    private AlocacaoResponseDTO resposta(Alocacao alocacao) {
        return alocacaoMapper.toResponse(alocacao);
    }

    private Long paroquiaId() {
        return usuarioLogado.paroquiaId();
    }

    private Long usuarioId() {
        return usuarioLogado.id();
    }
}
