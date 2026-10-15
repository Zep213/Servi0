package br.com.zep.servio.service.conta;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Limite por IP nas rotas públicas de conta (ativação, recuperação, proteção). Mesmo desenho do
 * {@code LimiteConfirmacoes}, com balde próprio: abrir a página do convite não gasta a cota de quem está
 * recuperando a senha. Janela deslizante de um minuto; o Caffeine descarta os IPs parados.
 */
@Component
public class LimiteContaPublica {

    private static final Duration JANELA = Duration.ofMinutes(1);

    private final int maxPorMinuto;

    private final Cache<String, Janela> janelas = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5))
            .maximumSize(100_000)
            .build();

    public LimiteContaPublica(@Value("${servio.conta.limite-por-minuto:20}") int maxPorMinuto) {
        this.maxPorMinuto = maxPorMinuto;
    }

    public boolean permitir(String ip) {
        return permitir(ip, Instant.now());
    }

    boolean permitir(String ip, Instant agora) {
        return janelas.get(ip, k -> new Janela()).registrar(agora, maxPorMinuto);
    }

    /** Janela deslizante: guarda os instantes das chamadas aceitas no último minuto. Sem rajada de 2x na virada. */
    static final class Janela {

        private final Deque<Instant> aceitas = new ArrayDeque<>();

        synchronized boolean registrar(Instant agora, int maximo) {
            while (!aceitas.isEmpty() && Duration.between(aceitas.peekFirst(), agora).compareTo(JANELA) >= 0) {
                aceitas.pollFirst();
            }
            if (aceitas.size() >= maximo) {
                return false;       // recusada não conta: quem insiste não estende o próprio bloqueio
            }
            aceitas.addLast(agora);
            return true;
        }
    }
}
