package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.mapper.CelebracaoMapper;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.CelebracaoResponseDTO;
import br.com.zep.servio.model.dto.EventoRequestDTO;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoData;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Evento criado pela coordenação da pastoral (Sprint 4, cartão 3). Quem cria: COORDENADOR da pastoral, PADRE ou ADMIN;
 * vice, técnico, secretário e membro não. O evento nasce {@code EVENTO}, ligado à pastoral de origem, e só essa
 * pastoral ganha as vagas dos seus modelos (as outras entram pelo padre, "responsabilizar a pastoral"). Editar ou
 * cancelar só enquanto ninguém estiver escalado. O que o padre faz em {@code /api/celebracoes} não muda.
 */
@Service
@RequiredArgsConstructor
public class EventoPastoralService {

    private final CelebracaoRepository celebracaoRepository;
    private final CelebracaoMapper mapper;
    private final ComunidadeRepository comunidadeRepository;
    private final PastoralRepository pastoralRepository;
    private final AlocacaoRepository alocacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CoberturaAutomaticaService coberturaAutomaticaService;
    private final ConfiguracaoPastoralService configuracaoPastoralService;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Transactional
    public CelebracaoResponseDTO criar(Long pastoralId, EventoRequestDTO request) {
        Pastoral pastoral = pastoral(pastoralId);
        exigirCoordenacao(pastoralId);
        exigirDataNaoPassada(request.data());

        Celebracao evento = new Celebracao();
        evento.setTipo(TipoCelebracao.EVENTO);
        evento.setPastoralOrigem(pastoral);
        evento.setParoquiaId(usuarioLogado.paroquiaId());
        aplicar(evento, request);
        Celebracao salvo = celebracaoRepository.save(evento);

        // só a pastoral de origem ganha vagas, e só se a cobertura automática dela estiver ligada
        if (configuracaoPastoralService.coberturaAutomaticaLigada(pastoralId)) {
            coberturaAutomaticaService.aplicarFuturas(pastoral, List.of(salvo));
        }
        auditar("EVENTO_CRIADO", salvo);
        return mapper.toResponse(salvo);
    }

    @Transactional
    public CelebracaoResponseDTO atualizar(Long pastoralId, Long celebracaoId, EventoRequestDTO request) {
        pastoral(pastoralId);
        exigirCoordenacao(pastoralId);
        Celebracao evento = eventoDaPastoral(pastoralId, celebracaoId);
        exigirSemEscalados(evento);
        exigirDataNaoPassada(request.data());
        aplicar(evento, request);
        Celebracao salvo = celebracaoRepository.save(evento);
        auditar("EVENTO_ALTERADO", salvo);
        return mapper.toResponse(salvo);
    }

    @Transactional
    public void desativar(Long pastoralId, Long celebracaoId) {
        pastoral(pastoralId);
        exigirCoordenacao(pastoralId);
        Celebracao evento = eventoDaPastoral(pastoralId, celebracaoId);
        exigirSemEscalados(evento);
        evento.setActive(false);
        celebracaoRepository.save(evento);
        auditar("EVENTO_CANCELADO", evento);
    }

    private void aplicar(Celebracao evento, EventoRequestDTO request) {
        Comunidade comunidade = comunidadeRepository
                .findByIdAndParoquiaIdAndActiveTrue(request.comunidadeId(), usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Comunidade", request.comunidadeId()));
        evento.setComunidade(comunidade);
        evento.setData(request.data());
        evento.setHora(request.hora());
        evento.setTitulo(request.titulo().trim());
        evento.setTipoData(request.tipoData() != null ? request.tipoData() : TipoData.NORMAL);
    }

    /** Evento que não é desta pastoral (ou não é evento) responde 404, como se não existisse. */
    private Celebracao eventoDaPastoral(Long pastoralId, Long celebracaoId) {
        Celebracao celebracao = celebracaoRepository
                .findByIdAndParoquiaIdAndActiveTrue(celebracaoId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Evento", celebracaoId));
        if (celebracao.getTipo() != TipoCelebracao.EVENTO || celebracao.getPastoralOrigem() == null
                || !celebracao.getPastoralOrigem().getId().equals(pastoralId)) {
            throw new RecursoNaoEncontradoException("Evento", celebracaoId);
        }
        return celebracao;
    }

    private void exigirSemEscalados(Celebracao evento) {
        if (alocacaoRepository.existsByVagaCelebracaoIdAndActiveTrueAndStatusIn(
                evento.getId(), StatusConvite.OCUPANTES)) {
            throw new RegraNegocioException("Há pessoas escaladas neste evento: peça ao padre");
        }
    }

    private void exigirDataNaoPassada(LocalDate data) {
        if (data.isBefore(LocalDate.now(clock))) {
            throw new RegraNegocioException("A data do evento já passou");
        }
    }

    private void exigirCoordenacao(Long pastoralId) {
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException("Pastoral", pastoralId);
        }
        if (!pastoraisPermissao.temPapel(pastoralId, "COORDENADOR")) {
            throw new AccessDeniedException("Só o coordenador da pastoral (ou padre/ADMIN) gerencia os eventos dela");
        }
    }

    private Pastoral pastoral(Long pastoralId) {
        return pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pastoral", pastoralId));
    }

    private void auditar(String tipoEvento, Celebracao evento) {
        Usuario autor = usuarioRepository.findById(usuarioLogado.id()).orElseThrow();
        auditLogService.registrar(tipoEvento, evento.getId(), autor, evento.getParoquiaId(),
                "Evento em " + evento.getData() + " às " + evento.getHora() + ", pastoral "
                        + evento.getPastoralOrigem().getId());
    }
}
