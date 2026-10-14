package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.NotificacaoEnviada;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import br.com.zep.servio.repository.NotificacaoEnviadaRepository;
import br.com.zep.servio.service.escalacao.evento.ConviteCriadoEvent;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ConviteService {

    private static final Duration INTERVALO_REENVIO = Duration.ofHours(1);

    private final TokenConvite tokenConvite;
    private final ConfiguracaoPastoralService configuracaoPastoralService;
    private final NotificacaoEnviadaRepository notificacaoEnviadaRepository;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;

    public LocalDateTime novoPrazo(Long pastoralId) {
        return LocalDateTime.now(clock).plusHours(configuracaoPastoralService.prazoRespostaHoras(pastoralId));
    }

    /**
     * Gera um token novo e publica o convite. CONVITE_REENVIO também grava o envio aqui, na
     * transação, para o limite de reenvio valer já no próximo pedido.
     */
    public void emitir(Alocacao alocacao, TipoNotificacao tipo) {
        String token = tokenConvite.gerar();
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
