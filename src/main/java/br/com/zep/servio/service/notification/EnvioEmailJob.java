package br.com.zep.servio.service.notification;

import br.com.zep.servio.model.enumerated.StatusEmail;
import br.com.zep.servio.repository.EmailSaidaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Retoma a fila de e-mails (Sprint 1 Cartão 1): a cada minuto tenta de novo o que está pronto
 * ({@code proxima_tentativa_em} vencida) e, de madrugada, apaga o que já foi enviado há muito tempo.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "servio.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class EnvioEmailJob {

    private static final int TAMANHO_LOTE = 50;
    private static final int DIAS_RETENCAO_ENVIADO = 30;

    private final EmailSaidaRepository repository;
    private final FilaEmail filaEmail;
    private final PlatformTransactionManager transacoes;
    private final Clock clock;

    /** fixedDelay (não fixedRate): não sobrepõe se um lote demorar mais que um minuto. */
    @Scheduled(fixedDelay = 60_000)
    public void processar() {
        LocalDateTime agora = LocalDateTime.now(clock);
        for (Long id : repository.idsProntos(StatusEmail.PENDENTE, agora, PageRequest.of(0, TAMANHO_LOTE))) {
            filaEmail.tentar(id);
        }
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "${servio.fuso}")
    public void limparAntigos() {
        LocalDateTime limite = LocalDateTime.now(clock).minusDays(DIAS_RETENCAO_ENVIADO);
        new TransactionTemplate(transacoes).executeWithoutResult(status -> repository.apagarEnviadosAntesDe(limite));
    }
}
