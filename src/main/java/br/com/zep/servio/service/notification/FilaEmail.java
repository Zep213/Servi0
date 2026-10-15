package br.com.zep.servio.service.notification;

import br.com.zep.servio.config.AsyncConfig;
import br.com.zep.servio.model.EmailSaida;
import br.com.zep.servio.model.enumerated.StatusEmail;
import br.com.zep.servio.repository.EmailSaidaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Fila de saída de e-mail (outbox, Sprint 1 Cartão 1): grava antes de mandar, então uma queda do
 * SMTP ou do próprio app não perde o e-mail — o {@code EnvioEmailJob} retoma pelo {@code email_saida}.
 *
 * <p><b>Lacuna que sobra:</b> entre o commit da ação (ex.: convite criado) e o
 * {@code @TransactionalEventListener} gravar nesta fila, o evento vive só em memória, por
 * milissegundos — se o processo cair exatamente nesse intervalo, o e-mail daquele evento específico
 * não sai e não há registro pra retomar. Fechar isso de verdade exige os listeners gravarem na fila
 * {@code AFTER_COMMIT} e sim, ANTES do commit da própria ação (ou um outbox por evento de domínio);
 * fica para depois.
 */
@Slf4j
@Component
public class FilaEmail {

    private final EmailSaidaRepository repository;
    private final Notificador notificador;
    private final PlatformTransactionManager transacoes;
    private final Executor emailExecutor;
    private final Clock clock;

    /**
     * Construtor escrito à mão: o Lombok não copia o @Qualifier para o parâmetro (mesmo motivo do
     * {@code EmailNotificador}), e sem ele o Spring não saberia qual Executor injetar aqui.
     */
    public FilaEmail(EmailSaidaRepository repository, Notificador notificador,
                     PlatformTransactionManager transacoes,
                     @Qualifier(AsyncConfig.EXECUTOR_EMAIL) Executor emailExecutor,
                     Clock clock) {
        this.repository = repository;
        this.notificador = notificador;
        this.transacoes = transacoes;
        this.emailExecutor = emailExecutor;
        this.clock = clock;
    }

    /** Atraso antes de cada nova tentativa, em minutos, pela ordem das falhas (1ª falha usa o índice 0). */
    private static final int[] ATRASOS_MINUTOS = {1, 5, 15, 60};
    private static final int MAX_TENTATIVAS = ATRASOS_MINUTOS.length + 1;

    public Long enfileirar(Long paroquiaId, String destinatario, String assunto, String template,
                           Map<String, Object> variaveis) {
        return gravarEDespachar(paroquiaId, destinatario, assunto, template, variaveis);
    }

    public Long enfileirarTexto(Long paroquiaId, String destinatario, String assunto, String texto) {
        return gravarEDespachar(paroquiaId, destinatario, assunto, null, Map.of("texto", texto));
    }

    private Long gravarEDespachar(Long paroquiaId, String destinatario, String assunto, String template,
                                  Map<String, Object> variaveis) {
        EmailSaida email = new EmailSaida();
        email.setParoquiaId(paroquiaId);
        email.setDestinatario(destinatario);
        email.setAssunto(assunto);
        email.setTemplate(template);
        email.setVariaveis(variaveis);
        email.setStatus(StatusEmail.PENDENTE);
        email.setCriadoEm(LocalDateTime.now(clock));
        email.setProximaTentativaEm(LocalDateTime.now(clock));

        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Long id = repository.save(email).getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        emailExecutor.execute(() -> tentar(id));
                    } catch (TaskRejectedException e) {
                        log.warn("Executor de e-mail recusou o envio imediato de {}; o job tenta depois", id);
                    }
                }
            });
            return id;
        }

        TransactionTemplate novaTransacao = new TransactionTemplate(transacoes);
        Long id = novaTransacao.execute(status -> repository.save(email).getId());
        tentar(id);
        return id;
    }

    /**
     * Tenta enviar um e-mail pendente, numa transação própria que trava a linha
     * ({@code for update skip locked}): se outra thread ou instância já estiver com ela, esta
     * chamada não faz nada. Chamado pelo despacho depois do commit, direto (sem transação do
     * chamador) e pelo {@code EnvioEmailJob}.
     */
    public void tentar(Long id) {
        TransactionTemplate tx = new TransactionTemplate(transacoes);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            EmailSaida email = repository.travarPendente(id).orElse(null);
            if (email == null) {
                return;
            }
            try {
                if (email.getTemplate() == null) {
                    notificador.notificar(email.getDestinatario(), email.getAssunto(),
                            (String) email.getVariaveis().get("texto"));
                } else {
                    notificador.enviar(email.getDestinatario(), email.getAssunto(), email.getTemplate(),
                            email.getVariaveis());
                }
                email.setStatus(StatusEmail.ENVIADO);
                email.setEnviadoEm(LocalDateTime.now(clock));
                email.setVariaveis(null);
            } catch (RuntimeException e) {
                registrarFalha(email, e);
            }
        });
    }

    private void registrarFalha(EmailSaida email, RuntimeException e) {
        Throwable raiz = causaRaiz(e);
        int tentativas = email.getTentativas() + 1;
        email.setTentativas((short) tentativas);
        email.setUltimoErro(resumo(raiz));
        if (tentativas >= MAX_TENTATIVAS) {
            email.setStatus(StatusEmail.FALHOU);
            email.setVariaveis(null);
        } else {
            email.setProximaTentativaEm(LocalDateTime.now(clock).plusMinutes(ATRASOS_MINUTOS[tentativas - 1]));
        }
        // Sem destinatário, sem token e sem corpo: só o id e a tentativa identificam o problema no log
        log.error("Falha ao enviar o e-mail {} (tentativa {})", email.getId(), tentativas, e);
    }

    private static Throwable causaRaiz(Throwable t) {
        Throwable raiz = t;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz;
    }

    private static String resumo(Throwable raiz) {
        String texto = raiz.getClass().getName() + ": " + raiz.getMessage();
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }
}
