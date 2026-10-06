package br.com.zep.servio.service.escalacao;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Limite de 30 chamadas por minuto e por IP nas rotas públicas do convite (detalhes e responder
 * compartilham o mesmo balde). Janela fixa de um minuto; o Caffeine descarta os IPs parados.
 */
@Component
public class LimiteConfirmacoes {

    static final int MAX_POR_MINUTO = 30;
    private static final Duration JANELA = Duration.ofMinutes(1);

    private final Cache<String, Janela> janelas = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
            .maximumSize(100_000)
            .build();

    public boolean permitir(String ip) {
        return janelas.get(ip, k -> new Janela()).registrar(Instant.now());
    }

    static final class Janela {

        private Instant inicio = Instant.EPOCH;
        private int contagem;

        synchronized boolean registrar(Instant agora) {
            if (Duration.between(inicio, agora).compareTo(JANELA) >= 0) {
                inicio = agora;
                contagem = 0;
            }
            contagem++;
            return contagem <= MAX_POR_MINUTO;
        }
    }
}
