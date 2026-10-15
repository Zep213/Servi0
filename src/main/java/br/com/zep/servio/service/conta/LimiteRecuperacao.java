package br.com.zep.servio.service.conta;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * No máximo 3 e-mails de recuperação por hora e por conta, para a caixa de uma pessoa não virar alvo de spam.
 * Passou disso, quem chama simplesmente não envia (a resposta pública continua a mesma). Janela fixa que
 * começa no primeiro pedido; usa o {@code Clock} da aplicação, como os jobs.
 */
@Component
public class LimiteRecuperacao {

    private static final Duration JANELA = Duration.ofHours(1);

    private final Clock clock;
    private final int maxPorHora;

    private final Cache<Long, Janela> janelas = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(100_000)
            .build();

    public LimiteRecuperacao(Clock clock, @Value("${servio.conta.recuperacao-max-por-hora:3}") int maxPorHora) {
        this.clock = clock;
        this.maxPorHora = maxPorHora;
    }

    /** Registra o pedido e diz se ainda cabe na janela. */
    public boolean permitir(Long usuarioId) {
        return janelas.get(usuarioId, k -> new Janela()).registrar(clock.instant(), maxPorHora);
    }

    static final class Janela {

        private Instant inicio = Instant.EPOCH;
        private int contagem;

        synchronized boolean registrar(Instant agora, int maximo) {
            if (Duration.between(inicio, agora).compareTo(JANELA) >= 0) {
                inicio = agora;
                contagem = 0;
            }
            contagem++;
            return contagem <= maximo;
        }
    }
}
