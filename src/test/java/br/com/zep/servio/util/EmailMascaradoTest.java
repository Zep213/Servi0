package br.com.zep.servio.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmailMascaradoTest {

    @Test
    void escondeOMiolo() {
        assertEquals("m***@gmail.com", EmailMascarado.mascarar("maria.silva@gmail.com"));
    }

    @Test
    void entradaInvalidaNaoVaza() {
        assertEquals("****", EmailMascarado.mascarar(null));
        assertEquals("****", EmailMascarado.mascarar("sem-arroba"));
        assertEquals("****", EmailMascarado.mascarar("@dominio.com"));
    }
}
