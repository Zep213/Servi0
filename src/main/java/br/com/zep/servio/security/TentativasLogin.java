package br.com.zep.servio.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Conta falhas de login por IP e por e-mail. O Caffeine expira as entradas sozinho,
 * então não há limpeza manual nem vazamento de memória.
 */
@Component
public class TentativasLogin {

    public static final int MAX_FALHAS = 5;
    public static final Duration JANELA = Duration.ofMinutes(15);

    private final Cache<String, AtomicInteger> falhas = Caffeine.newBuilder()
            .expireAfterWrite(JANELA)
            .maximumSize(100_000)
            .build();

    public boolean bloqueado(String chave) {
        AtomicInteger contador = falhas.getIfPresent(chave);
        return contador != null && contador.get() >= MAX_FALHAS;
    }

    public void registrarFalha(String chave) {
        falhas.get(chave, k -> new AtomicInteger()).incrementAndGet();
    }

    public void limpar(String chave) {
        falhas.invalidate(chave);
    }

    public static String chaveIp(HttpServletRequest request) {
        return "ip:" + request.getRemoteAddr();
    }

    public static String chaveEmail(String email) {
        return "email:" + email.trim().toLowerCase(Locale.ROOT);
    }
}
