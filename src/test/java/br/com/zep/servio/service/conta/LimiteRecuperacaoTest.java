package br.com.zep.servio.service.conta;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiteRecuperacaoTest {

    /** Relógio que o teste empurra para frente. */
    static final class RelogioMutavel extends Clock {
        Instant agora = Instant.parse("2026-11-09T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return agora;
        }
    }

    private final RelogioMutavel relogio = new RelogioMutavel();
    private final LimiteRecuperacao limite = new LimiteRecuperacao(relogio, 3);

    @Test
    void tresPorHoraEAQuartaNao() {
        assertTrue(limite.permitir(1L));
        assertTrue(limite.permitir(1L));
        assertTrue(limite.permitir(1L));
        assertFalse(limite.permitir(1L));
    }

    @Test
    void depoisDeUmaHoraLibera() {
        for (int i = 0; i < 4; i++) {
            limite.permitir(2L);
        }
        relogio.agora = relogio.agora.plusSeconds(59 * 60);
        assertFalse(limite.permitir(2L));
        relogio.agora = relogio.agora.plusSeconds(61);
        assertTrue(limite.permitir(2L));
    }

    @Test
    void cadaContaTemOProprioLimite() {
        for (int i = 0; i < 5; i++) {
            limite.permitir(3L);
        }
        assertTrue(limite.permitir(4L));
    }
}
