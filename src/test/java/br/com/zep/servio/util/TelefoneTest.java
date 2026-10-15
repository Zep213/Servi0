package br.com.zep.servio.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelefoneTest {

    @ParameterizedTest
    @ValueSource(strings = {"(83) 9 8812-4407", "83988124407", "+55 83 98812-4407", "083988124407",
            "0 83 98812-4407", "5583988124407"})
    void celularEmVariosFormatosViraOMesmoNumero(String entrada) {
        assertEquals(Optional.of("+5583988124407"), Telefone.normalizar(entrada));
    }

    @Test
    void fixoTem10Digitos() {
        assertEquals(Optional.of("+558333211234"), Telefone.normalizar("(83) 3321-1234"));
    }

    @Test
    void dddComecandoComZeroEhRecusadoPelaRegraDoDdd() {
        // 11 dígitos: depois de tirar o zero de tronco sobram 10 ("0988124407"), tamanho válido;
        // só a regra do DDD sem zero recusa
        assertTrue(Telefone.normalizar("(00) 98812-4407").isEmpty());
        assertTrue(Telefone.normalizar("0 0 83 3321-1234").isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "abc", "telefone", "3321-1234", "12345678", "(00) 98812-4407",
            "(83) 88812-4407", "559883321123456"})
    void invalidosViramVazio(String entrada) {
        assertTrue(Telefone.normalizar(entrada).isEmpty());
    }

    @Test
    void mascararEscondeOMioloDoNumero() {
        assertEquals("+55839****4407", Telefone.mascarar("+5583988124407"));
        assertEquals("****", Telefone.mascarar(null));
    }

    @Test
    void ehTelefoneDistingueDeEmail() {
        assertTrue(Telefone.ehTelefone("(83) 98812-4407"));
        assertTrue(Telefone.ehTelefone("83988124407"));
        assertFalse(Telefone.ehTelefone("maria@servio.dev"));
        assertFalse(Telefone.ehTelefone("83@988124407"));
        assertFalse(Telefone.ehTelefone("123456789"));
        assertFalse(Telefone.ehTelefone(null));
    }
}
