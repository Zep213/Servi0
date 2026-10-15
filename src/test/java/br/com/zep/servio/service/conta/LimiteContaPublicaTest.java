package br.com.zep.servio.service.conta;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimiteContaPublicaTest {

    private final LimiteContaPublica limite = new LimiteContaPublica(20);
    private final Instant inicio = Instant.parse("2026-11-09T12:00:00Z");

    @Test
    void vinteChamadasPassamEAVinteEUmaEhRecusada() {
        for (int i = 0; i < 20; i++) {
            assertTrue(limite.permitir("10.0.0.1", inicio.plusSeconds(i)), "chamada " + (i + 1));
        }
        assertFalse(limite.permitir("10.0.0.1", inicio.plusSeconds(30)));
    }

    @Test
    void novaJanelaLibera() {
        for (int i = 0; i < 21; i++) {
            limite.permitir("10.0.0.2", inicio);
        }
        assertFalse(limite.permitir("10.0.0.2", inicio.plusSeconds(59)));
        assertTrue(limite.permitir("10.0.0.2", inicio.plusSeconds(60)));
    }

    @Test
    void cadaIpTemOProprioBalde() {
        for (int i = 0; i < 25; i++) {
            limite.permitir("10.0.0.3", inicio);
        }
        assertTrue(limite.permitir("10.0.0.4", inicio));
    }

    @Test
    void semRajadaDeDuasVezesNaViradaDoMinuto() {
        // 20 no fim do primeiro minuto: com janela fixa, +20 logo depois seriam aceitas
        for (int i = 0; i < 20; i++) {
            assertTrue(limite.permitir("10.0.0.5", inicio.plusSeconds(59)));
        }
        assertFalse(limite.permitir("10.0.0.5", inicio.plusSeconds(60)));
        assertFalse(limite.permitir("10.0.0.5", inicio.plusSeconds(118)));
        assertTrue(limite.permitir("10.0.0.5", inicio.plusSeconds(119)));
    }

    @Test
    void recusadaNaoEstendeOBloqueio() {
        for (int i = 0; i < 20; i++) {
            limite.permitir("10.0.0.6", inicio);
        }
        for (int i = 1; i < 60; i++) {
            assertFalse(limite.permitir("10.0.0.6", inicio.plusSeconds(i)));
        }
        assertTrue(limite.permitir("10.0.0.6", inicio.plusSeconds(60)));
    }
}
