package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.EmailSaida;
import br.com.zep.servio.model.enumerated.StatusEmail;
import br.com.zep.servio.repository.EmailSaidaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.notification.EnvioEmailJob;
import br.com.zep.servio.service.notification.FilaEmail;
import br.com.zep.servio.service.notification.Notificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Fila de e-mails/outbox (Sprint 1, Cartão 1). Relógio fixo, como no {@code JobsIT}. Sem
 * {@code servio.jobs.enabled=true}: o {@code EnvioEmailJob} é instanciado à mão em cada teste
 * (o bean nem existe no contexto de teste, por causa do {@code @ConditionalOnProperty}) — ligar o
 * agendador faria o job de verdade rodar sozinho no meio do teste e embaralhar as contagens.
 *
 * <p>O banco do Testcontainers é compartilhado entre os ITs (outras classes deixam e-mails
 * {@code PENDENTE} na tabela, ex.: o {@code NotificacaoIT} com SMTP falhando de propósito), por
 * isso cada teste usa um destinatário único e só confere linhas filtradas por ele.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class FilaEmailIT {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    static final RelogioDeTeste RELOGIO = new RelogioDeTeste(Instant.now());

    @TestConfiguration
    static class ConfigRelogio {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return RELOGIO;
        }
    }

    static final class RelogioDeTeste extends Clock {
        private volatile Instant instante;

        RelogioDeTeste(Instant instante) {
            this.instante = instante;
        }

        @Override
        public ZoneId getZone() {
            return FUSO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }

    @Autowired
    FilaEmail filaEmail;
    @Autowired
    EmailSaidaRepository repository;
    @Autowired
    PlatformTransactionManager transacoes;
    @MockitoBean
    Notificador notificador;

    @BeforeEach
    void setUp() {
        RELOGIO.instante = Instant.now();
    }

    private static String unico() {
        return "filaemail." + UUID.randomUUID() + "@teste.dev";
    }

    private EnvioEmailJob job() {
        return new EnvioEmailJob(repository, filaEmail, transacoes, RELOGIO);
    }

    @Test
    void smtpFalhaNaPrimeiraTentativaEEnviaNaSeguinte() {
        String destinatario = unico();
        doThrow(new IllegalStateException("SMTP fora do ar")).doNothing()
                .when(notificador).notificar(eq(destinatario), any(), any());

        Long id = filaEmail.enfileirarTexto(null, destinatario, "Assunto", "Texto");

        EmailSaida depoisDaFalha = repository.findById(id).orElseThrow();
        assertThat(depoisDaFalha.getStatus()).isEqualTo(StatusEmail.PENDENTE);
        assertThat(depoisDaFalha.getTentativas()).isEqualTo((short) 1);
        assertThat(depoisDaFalha.getUltimoErro()).contains("IllegalStateException", "SMTP fora do ar");

        RELOGIO.instante = RELOGIO.instante.plus(90, ChronoUnit.SECONDS);
        job().processar();

        EmailSaida depoisDoRetry = repository.findById(id).orElseThrow();
        assertThat(depoisDoRetry.getStatus()).isEqualTo(StatusEmail.ENVIADO);
        assertThat(depoisDoRetry.getEnviadoEm()).isNotNull();
        verify(notificador, times(2)).notificar(eq(destinatario), any(), any());
    }

    @Test
    void depoisDeCincoFalhasFicaFalhouComVariaveisNula() {
        String destinatario = unico();
        doThrow(new IllegalStateException("SMTP fora do ar")).when(notificador)
                .notificar(eq(destinatario), any(), any());

        Long id = filaEmail.enfileirarTexto(null, destinatario, "Assunto", "Texto");
        for (int i = 0; i < 4; i++) {
            RELOGIO.instante = RELOGIO.instante.plus(2, ChronoUnit.HOURS);
            job().processar();
        }

        EmailSaida falhou = repository.findById(id).orElseThrow();
        assertThat(falhou.getStatus()).isEqualTo(StatusEmail.FALHOU);
        assertThat(falhou.getTentativas()).isEqualTo((short) 5);
        assertThat(falhou.getVariaveis()).isNull();
        verify(notificador, times(5)).notificar(eq(destinatario), any(), any());
    }

    @Test
    void depoisDoEnvioVariaveisFicaNulaEEnviadoEmPreenchido() {
        String destinatario = unico();

        Long id = filaEmail.enfileirarTexto(null, destinatario, "Assunto", "Texto");

        EmailSaida enviado = repository.findById(id).orElseThrow();
        assertThat(enviado.getStatus()).isEqualTo(StatusEmail.ENVIADO);
        assertThat(enviado.getEnviadoEm()).isNotNull();
        assertThat(enviado.getVariaveis()).isNull();
        verify(notificador, times(1)).notificar(eq(destinatario), any(), any());
    }

    @Test
    void duasTentativasSimultaneasEnviamUmaVezSo() throws InterruptedException {
        String destinatario = unico();
        EmailSaida email = new EmailSaida();
        email.setDestinatario(destinatario);
        email.setAssunto("Assunto");
        email.setTemplate(null);
        email.setVariaveis(Map.of("texto", "Texto"));
        email.setStatus(StatusEmail.PENDENTE);
        email.setCriadoEm(LocalDateTime.now(RELOGIO));
        email.setProximaTentativaEm(LocalDateTime.now(RELOGIO));
        Long id = repository.save(email).getId();

        CountDownLatch travando = new CountDownLatch(1);
        CountDownLatch podeLiberar = new CountDownLatch(1);
        doAnswer(invocacao -> {
            travando.countDown();
            podeLiberar.await(5, TimeUnit.SECONDS);
            return null;
        }).when(notificador).notificar(eq(destinatario), any(), any());

        Thread t1 = new Thread(() -> filaEmail.tentar(id));
        t1.start();
        assertThat(travando.await(5, TimeUnit.SECONDS)).isTrue();

        // t1 ainda está "dentro" do envio, com a transação (e a trava da linha) aberta.
        Thread t2 = new Thread(() -> filaEmail.tentar(id));
        t2.start();
        t2.join(5000);

        podeLiberar.countDown();
        t1.join(5000);

        verify(notificador, times(1)).notificar(eq(destinatario), any(), any());
        EmailSaida resultado = repository.findById(id).orElseThrow();
        assertThat(resultado.getStatus()).isEqualTo(StatusEmail.ENVIADO);
    }
}
