package br.com.zep.servio.service.escalacao.job;

import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.service.escalacao.evento.ConviteExpiradoEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Expira convites pendentes com prazo vencido (Etapa 6, Parte 5). Roda sem usuário logado e cuida
 * de todas as paróquias de uma vez: cada convite é expirado na própria transação, com UPDATE
 * condicional. Rodar duas vezes não expira nem avisa duas vezes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "servio.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiracaoConvitesJob {

    private final AlocacaoRepository alocacaoRepository;
    private final ApplicationEventPublisher eventos;
    private final PlatformTransactionManager transacoes;
    private final Clock clock;

    @Scheduled(cron = "0 */10 * * * *", zone = "${servio.fuso}")
    public void executar() {
        LocalDateTime agora = LocalDateTime.now(clock);
        TransactionTemplate tx = new TransactionTemplate(transacoes);
        for (Long id : alocacaoRepository.idsPendentesVencidos(StatusConvite.PENDENTE, agora)) {
            try {
                tx.executeWithoutResult(status -> {
                    int expirados = alocacaoRepository.expirarSePendente(
                            id, StatusConvite.PENDENTE, StatusConvite.EXPIRADA, agora);
                    if (expirados == 1) {
                        // Só publica quando este job é quem expirou: o aviso sai uma vez por convite
                        eventos.publishEvent(new ConviteExpiradoEvent(id));
                    }
                });
            } catch (RuntimeException e) {
                log.error("Falha ao expirar a alocação {}", id, e);
            }
        }
    }
}
