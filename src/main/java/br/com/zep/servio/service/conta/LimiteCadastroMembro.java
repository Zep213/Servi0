package br.com.zep.servio.service.conta;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Limite de cadastros de membro por quem cadastra (chave {@code cadastro:<id>}): sem ele, o 409 de e-mail
 * repetido serviria de oráculo de e-mails do sistema todo e cada cadastro dispara um e-mail. Janela deslizante
 * de uma hora; toda tentativa aceita pelo limite conta, inclusive as que terminam em 409.
 */
@Component
public class LimiteCadastroMembro {

    private static final Duration JANELA = Duration.ofHours(1);

    private final int maxPorHora;
    private final Clock clock;

    private final Cache<String, Deque<Instant>> janelas = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofHours(2))
            .maximumSize(10_000)
            .build();

    public LimiteCadastroMembro(@Value("${servio.conta.cadastro-membro-por-hora:20}") int maxPorHora, Clock clock) {
        this.maxPorHora = maxPorHora;
        this.clock = clock;
    }

    public boolean permitir(Long usuarioId) {
        Instant agora = clock.instant();
        Deque<Instant> aceitas = janelas.get("cadastro:" + usuarioId, k -> new ArrayDeque<>());
        synchronized (aceitas) {
            while (!aceitas.isEmpty() && Duration.between(aceitas.peekFirst(), agora).compareTo(JANELA) >= 0) {
                aceitas.pollFirst();
            }
            if (aceitas.size() >= maxPorHora) {
                return false;
            }
            aceitas.addLast(agora);
            return true;
        }
    }
}
