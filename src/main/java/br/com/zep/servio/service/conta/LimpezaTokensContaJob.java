package br.com.zep.servio.service.conta;

import br.com.zep.servio.repository.TokenContaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

/** De madrugada, apaga token de conta usado ou vencido há mais de 30 dias. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "servio.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class LimpezaTokensContaJob {

    private static final int DIAS_RETENCAO = 30;

    private final TokenContaRepository repository;
    private final PlatformTransactionManager transacoes;
    private final Clock clock;

    @Scheduled(cron = "0 30 3 * * *", zone = "${servio.fuso}")
    public void limpar() {
        LocalDateTime limite = LocalDateTime.now(clock).minusDays(DIAS_RETENCAO);
        Integer apagados = new TransactionTemplate(transacoes).execute(status -> repository.apagarAntesDe(limite));
        log.info("Limpeza de tokens de conta: {} apagados", apagados);
    }
}
