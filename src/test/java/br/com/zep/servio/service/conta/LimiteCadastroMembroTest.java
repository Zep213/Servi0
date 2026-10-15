package br.com.zep.servio.service.conta;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiteCadastroMembroTest {

    private static final class RelogioManual extends Clock {
        Instant agora = Instant.parse("2026-11-09T12:00:00Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioManual relogio = new RelogioManual();
    private final LimiteCadastroMembro limite = new LimiteCadastroMembro(20, relogio);

    @Test
    void vinteCadastrosPorHoraEAVinteEUmaERecusada() {
        for (int i = 0; i < 20; i++) {
            assertTrue(limite.permitir(1L), "cadastro " + (i + 1));
        }
        assertFalse(limite.permitir(1L));
    }

    @Test
    void depoisDeUmaHoraLibera() {
        for (int i = 0; i < 20; i++) {
            limite.permitir(2L);
        }
        relogio.agora = relogio.agora.plusSeconds(3599);
        assertFalse(limite.permitir(2L));
        relogio.agora = relogio.agora.plusSeconds(1);
        assertTrue(limite.permitir(2L));
    }

    @Test
    void cadaCoordenadorTemOProprioLimite() {
        for (int i = 0; i < 25; i++) {
            limite.permitir(3L);
        }
        assertTrue(limite.permitir(4L));
    }
}
