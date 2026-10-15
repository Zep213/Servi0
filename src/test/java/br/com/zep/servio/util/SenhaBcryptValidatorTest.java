package br.com.zep.servio.util;

import br.com.zep.servio.util.validacao.SenhaBcryptValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SenhaBcryptValidatorTest {

    private final SenhaBcryptValidator validador = new SenhaBcryptValidator();

    private boolean valida(String senha) {
        return validador.isValid(senha, null);
    }

    @Test
    void limitesEmCaracteresAscii() {
        assertFalse(valida("1234567"));
        assertTrue(valida("12345678"));
        assertTrue(valida("a".repeat(72)));
        assertFalse(valida("a".repeat(73)));
    }

    @Test
    void acentosContamPorBytes() {
        assertTrue(valida("é".repeat(36)), "36 x 2 bytes = 72 bytes");
        assertFalse(valida("é".repeat(37)), "74 bytes passa do limite do BCrypt");
        assertFalse(valida("é".repeat(40)));
    }

    @Test
    void emojiTemQuatroBytes() {
        assertTrue(valida("😀".repeat(18)), "18 x 4 bytes = 72 bytes");
        assertFalse(valida("😀".repeat(19)));
    }

    @Test
    void nuloFicaComNotBlank() {
        assertTrue(valida(null));
    }
}
