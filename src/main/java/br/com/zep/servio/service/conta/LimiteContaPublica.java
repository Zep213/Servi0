package br.com.zep.servio.service.conta;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Limite por IP nas rotas públicas de conta (ativação, recuperação, proteção). Mesmo desenho do
 * {@code LimiteConfirmacoes}, com balde próprio: abrir a página do convite não gasta a cota de quem está
 * recuperando a senha. Janela fixa de um minuto; o Caffeine descarta os IPs parados.
 */
@Component
public class LimiteContaPublica {

    private static final Duration JANELA = Duration.ofMinutes(1);

    private final int maxPorMinuto;

    private final Cache<String, Janela> janelas = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(5))
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
