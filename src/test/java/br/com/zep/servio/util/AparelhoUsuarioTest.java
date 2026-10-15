package br.com.zep.servio.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AparelhoUsuarioTest {

    @Test
    void iPhone() {
        assertEquals("Celular iPhone", AparelhoUsuario.descrever(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                        + "Version/17.5 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void celularAndroid() {
        assertEquals("Celular Android", AparelhoUsuario.descrever(
                "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/126.0.0.0 Mobile Safari/537.36"));
    }

    @Test
    void tabletAndroidENaoCelularPorqueFaltaMobile() {
        assertEquals("Tablet", AparelhoUsuario.descrever(
                "Mozilla/5.0 (Linux; Android 13; SM-X700) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/126.0.0.0 Safari/537.36"));
    }

    @Test
    void iPad() {
        assertEquals("Tablet", AparelhoUsuario.descrever(
                "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                        + "Version/17.5 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void windows() {
        assertEquals("Computador Windows", AparelhoUsuario.descrever(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/126.0.0.0 Safari/537.36"));
    }

    @Test
    void mac() {
        assertEquals("Computador Mac", AparelhoUsuario.descrever(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) "
                        + "Version/17.5 Safari/605.1.15"));
    }

    @Test
    void linux() {
        assertEquals("Computador Linux", AparelhoUsuario.descrever(
                "Mozilla/5.0 (X11; Linux x86_64; rv:127.0) Gecko/20100101 Firefox/127.0"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "curl/8.4.0", "lixo qualquer", "PlayStation"})
    void vazioOuForaDoPadraoViraDesconhecido(String userAgent) {
        assertEquals("aparelho desconhecido", AparelhoUsuario.descrever(userAgent));
    }
}
