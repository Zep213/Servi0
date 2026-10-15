package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.NotificacaoEnviada;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.NotificacaoEnviadaRepository;
import br.com.zep.servio.service.escalacao.evento.ConviteCriadoEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Emite convites: token novo (o anterior deixa de valer), prazo e o evento que manda o e-mail
 * depois do commit. Quem chama precisa já ter salvo a alocação (o id entra no evento).
 */
@Service
public class ConviteService {

    private static final Duration INTERVALO_REENVIO = Duration.ofHours(1);

    private final TokenConvite tokenConvite;
    private final ConfiguracaoPastoralService configuracaoPastoralService;
    private final NotificacaoEnviadaRepository notificacaoEnviadaRepository;
    private final AlocacaoRepository alocacaoRepository;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final Duration margemAntesDoEvento;

    public ConviteService(TokenConvite tokenConvite, ConfiguracaoPastoralService configuracaoPastoralService,
                          NotificacaoEnviadaRepository notificacaoEnviadaRepository,
                          AlocacaoRepository alocacaoRepository, ApplicationEventPublisher eventos, Clock clock,
                          @Value("${servio.convite.margem-minutos-antes-do-evento:0}") long margemMinutos) {
        this.tokenConvite = tokenConvite;
        this.configuracaoPastoralService = configuracaoPastoralService;
        this.notificacaoEnviadaRepository = notificacaoEnviadaRepository;
        this.alocacaoRepository = alocacaoRepository;
        this.eventos = eventos;
        this.clock = clock;
        this.margemAntesDoEvento = Duration.ofMinutes(margemMinutos);
    }

    /**
     * Prazo de um convite novo: o da pastoral (padrão 24h), mas nunca depois da hora da celebração menos a
     * margem ({@code servio.convite.margem-minutos-antes-do-evento}, padrão 0). Celebração que já começou
     * (ou passou do limite) não recebe convite: 422.
     */
    public LocalDateTime novoPrazo(Long pastoralId, Celebracao celebracao) {
        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDateTime limite = limiteDaCelebracao(celebracao);
        if (!limite.isAfter(agora)) {
            throw new RegraNegocioException("Esta celebração já começou");
        }
        LocalDateTime prazo = agora.plusHours(configuracaoPastoralService.prazoRespostaHoras(pastoralId));
        return prazo.isAfter(limite) ? limite : prazo;
    }

    /**
     * A data ou a hora da celebração mudou: os convites pendentes dela passam a vencer, no máximo, no novo limite.
     * Só diminui o prazo (mudar a missa para mais tarde não reabre convite que já ia vencer antes) e não manda
     * e-mail nem troca o token. Se o novo limite já passou, o ExpiracaoConvitesJob expira o convite no próximo ciclo.
     */
    public void ajustarPrazosPendentes(Celebracao celebracao) {
        LocalDateTime limite = limiteDaCelebracao(celebracao);
        for (Alocacao alocacao : alocacaoRepository.pendentesDaCelebracao(celebracao.getId(), StatusConvite.PENDENTE)) {
            if (alocacao.getDataLimiteResposta() != null && alocacao.getDataLimiteResposta().isAfter(limite)) {
                alocacao.setDataLimiteResposta(limite);
                alocacaoRepository.save(alocacao);
            }
        }
    }

    private LocalDateTime limiteDaCelebracao(Celebracao celebracao) {
        return celebracao.getData().atTime(celebracao.getHora()).minus(margemAntesDoEvento);
    }

    /**
     * Gera um token novo e publica o convite. CONVITE_REENVIO também grava o envio aqui, na
     * transação, para o limite de reenvio valer já no próximo pedido.
     */
    public void emitir(Alocacao alocacao, TipoNotificacao tipo) {
        String token = tokenConvite.gerar();
        alocacao.invalidarTokens();
        alocacao.setTokenHash(tokenConvite.hash(token));
        if (tipo == TipoNotificacao.CONVITE_REENVIO) {
            registrarEnvio(alocacao, tipo);
        }
        eventos.publishEvent(new ConviteCriadoEvent(alocacao.getId(), token, tipo));
    }

    /** Reenvio manual: no máximo um por hora para cada convite. O prazo não muda. */
    public void reenviar(Alocacao alocacao) {
        LocalDateTime limite = LocalDateTime.now(clock).minus(INTERVALO_REENVIO);
        boolean reenviadoAgora = notificacaoEnviadaRepository
                .findByAlocacaoIdAndTipo(alocacao.getId(), TipoNotificacao.CONVITE_REENVIO)
                .map(envio -> envio.getEnviadaEm().isAfter(limite))
                .orElse(false);
        if (reenviadoAgora) {
            throw new RegraNegocioException("Este convite já foi reenviado na última hora");
        }
        emitir(alocacao, TipoNotificacao.CONVITE_REENVIO);
    }

    /** Uma linha por (alocacao, tipo): o índice único pede upsert, e não insert, num segundo reenvio. */
    private void registrarEnvio(Alocacao alocacao, TipoNotificacao tipo) {
        NotificacaoEnviada envio = notificacaoEnviadaRepository
                .findByAlocacaoIdAndTipo(alocacao.getId(), tipo)
                .orElseGet(NotificacaoEnviada::new);
        envio.setParoquiaId(alocacao.getParoquiaId());
        envio.setAlocacao(alocacao);
        envio.setTipo(tipo);
        envio.setEnviadaEm(LocalDateTime.now(clock));
        notificacaoEnviadaRepository.save(envio);
    }
}
