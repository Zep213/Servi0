package br.com.zep.servio.service.agenda;

import br.com.zep.servio.model.HorarioMissa;
import br.com.zep.servio.repository.HorarioMissaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mantém as próximas semanas de missa geradas a partir dos horários fixos (Sprint 4, cartão 2). Roda de madrugada
 * (03:30, no fuso da paróquia) e uma vez quando a aplicação sobe, para um deploy não esperar até o dia seguinte.
 * Cuida de todas as paróquias; cada horário é gerado na própria transação e uma falha num horário não para os
 * outros: a execução seguinte cria o que faltou. Rodar duas vezes não duplica nada (índice único). Na pausa de
 * Natal (21/12 a 03/01) continua rodando sozinho: é ele que faz as missas de janeiro aparecerem.
 *
 * <p>Log só com ids e contagens, nunca com nomes de pessoas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "servio.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class GeracaoMissasJob {

    private final HorarioMissaRepository horarioMissaRepository;
    private final GeracaoMissasService geracaoMissasService;
    private final PlatformTransactionManager transacoes;

    @Scheduled(cron = "0 30 3 * * *", zone = "${servio.fuso}")
    public void executar() {
        TransactionTemplate tx = new TransactionTemplate(transacoes);
        int criadas = 0;
        int falhas = 0;
        for (Long horarioId : horarioMissaRepository.idsAtivos()) {
            try {
                Integer novas = tx.execute(status -> {
                    HorarioMissa horario = horarioMissaRepository.findById(horarioId).orElse(null);
                    return horario == null ? 0 : geracaoMissasService.gerar(horario);
                });
                criadas += novas == null ? 0 : novas;
            } catch (RuntimeException e) {
                falhas++;
                log.error("Falha ao gerar as missas do horário {}", horarioId, e);
            }
        }
        log.info("Geração de missas: {} criadas, {} horários com falha", criadas, falhas);
    }

    /** Uma execução ao subir; nunca derruba a subida. */
    @EventListener(ApplicationReadyEvent.class)
    public void aoSubir() {
        try {
            executar();
        } catch (RuntimeException e) {
            log.error("Falha na geração de missas ao subir a aplicação", e);
        }
    }
}
