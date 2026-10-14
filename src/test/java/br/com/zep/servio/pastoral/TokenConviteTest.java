package br.com.zep.servio.pastoral;

import br.com.zep.servio.service.escalacao.TokenConvite;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TokenConviteTest {

    private final TokenConvite tokens = new TokenConvite();

    @Test
    void token_tem_32_bytes_em_base64_url_sem_padding() {
        String token = tokens.gerar();

        assertThat(token).matches(Pattern.compile("[A-Za-z0-9_-]{43}"));
        assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
    }

    @Test
    void hash_e_sha256_hexadecimal_minusculo_de_64_caracteres() {
        // vetor conhecido de SHA-256("abc")
        assertThat(tokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hash_nao_e_o_proprio_token_e_e_estavel() {
        String token = tokens.gerar();

        assertThat(tokens.hash(token)).isNotEqualTo(token).isEqualTo(tokens.hash(token));
    }

    @Test
    void dez_mil_tokens_nao_se_repetem() {
        Set<String> gerados = new HashSet<>();
        IntStream.range(0, 10_000).forEach(i -> gerados.add(tokens.gerar()));

        assertThat(gerados).hasSize(10_000);
    }
}
