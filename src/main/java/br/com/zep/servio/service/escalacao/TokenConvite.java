package br.com.zep.servio.service.escalacao;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Token do link de convite. O token puro vai só para o e-mail; o banco guarda apenas o
 * SHA-256 (token_hash), então um vazamento do banco não entrega links válidos.
 */
@Component
public class TokenConvite {

    private static final int BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    /** 32 bytes aleatórios em Base64 URL-safe, sem padding (43 caracteres). */
    public String gerar() {
        byte[] bytes = new byte[BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal minúsculo: 64 caracteres, o tamanho de token_hash CHAR(64). */
    public String hash(String token) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(sha256.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }
}
