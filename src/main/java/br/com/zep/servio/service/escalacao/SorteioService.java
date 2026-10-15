package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;

/**
 * Sorteio justo de vagas. Embaralha os elegíveis e depois ordena, de forma estável, por quem
 * está há mais tempo sem servir (quem nunca serviu vem primeiro). O embaralhamento só decide
 * os empates. O gerador é injetado: em produção é o SecureRandom, nos testes uma semente fixa.
 */
@Service
@RequiredArgsConstructor
public class SorteioService {

    private final RandomGenerator random;

    public List<Usuario> escolher(List<CandidatoAvaliado> candidatos, int quantidade) {
        if (quantidade <= 0) {
            return List.of();
        }
        List<CandidatoAvaliado> embaralhados = new ArrayList<>(candidatos.stream()
                .filter(CandidatoAvaliado::elegivel)
                .toList());
        // Fisher-Yates com o gerador injetado (Collections.shuffle exigiria java.util.Random).
        for (int i = embaralhados.size() - 1; i > 0; i--) {
            Collections.swap(embaralhados, i, random.nextInt(i + 1));
        }
        embaralhados.sort(Comparator.comparing(CandidatoAvaliado::ultimaVezQueServiu,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return embaralhados.stream()
                .limit(quantidade)
                .map(CandidatoAvaliado::usuario)
                .toList();
    }

    /** Uma vaga a sortear: {@code faltam} é quanto ainda falta preencher; {@code id} desempata a ordem. */
    public record PedidoVaga<V>(V vaga, Long id, int faltam) {
    }

    /** O que saiu do sorteio de uma vaga: quem foi escolhido e quanto ficou faltando. */
    public record ResultadoVaga<V>(V vaga, Long id, int faltam, List<Usuario> escolhidos) {

        public int restam() {
            return faltam - escolhidos.size();
        }
    }

    /**
     * Sorteia várias vagas da mesma celebração começando pela mais apertada, recalculada a cada passo (guloso
     * dinâmico). Uma pessoa com mais de uma habilidade pode servir em vagas diferentes, mas só em uma delas por
     * celebração; em ordem de id, ela poderia ser gasta numa vaga folgada e deixar a apertada vazia.
     *
     * <p>A cada rodada, para cada vaga que ainda falta, {@code avaliar} devolve os candidatos de agora (já sem quem
     * foi escolhido nas rodadas anteriores, desde que {@code aplicar} tenha gravado a escolha). Folga = elegíveis -
     * faltam. Vai primeiro a vaga que **ainda dá para completar** (folga >= 0) de menor folga; empate: menos
     * elegíveis, depois menor id. Vagas que já são caso perdido (folga &lt; 0) vão por último, para não gastarem
     * quem ainda completa outra vaga. A escolha dentro da vaga é a de {@link #escolher} (quem serviu há mais tempo,
     * embaralhamento só nos empates), com o mesmo gerador: mesma semente, mesmo resultado.
     *
     * @return um resultado por pedido, na ordem em que os pedidos chegaram (não na ordem do sorteio)
     */
    public <V> List<ResultadoVaga<V>> sortearPorAperto(List<PedidoVaga<V>> pedidos,
                                                       Function<V, List<CandidatoAvaliado>> avaliar,
                                                       BiConsumer<V, List<Usuario>> aplicar) {
        List<PedidoVaga<V>> restantes = new ArrayList<>(pedidos);
        Map<Long, ResultadoVaga<V>> feitos = new HashMap<>();

        while (!restantes.isEmpty()) {
            Map<Long, List<CandidatoAvaliado>> candidatos = new HashMap<>();
            PedidoVaga<V> escolhida = null;
            Aperto apertoDaEscolhida = null;
            for (PedidoVaga<V> pedido : restantes) {
                List<CandidatoAvaliado> deAgora = avaliar.apply(pedido.vaga());
                candidatos.put(pedido.id(), deAgora);
                Aperto aperto = new Aperto((int) deAgora.stream().filter(CandidatoAvaliado::elegivel).count(),
                        pedido.faltam(), pedido.id());
                if (apertoDaEscolhida == null || ORDEM_DE_APERTO.compare(aperto, apertoDaEscolhida) < 0) {
                    escolhida = pedido;
                    apertoDaEscolhida = aperto;
                }
            }
            List<Usuario> escolhidos = escolher(candidatos.get(escolhida.id()), escolhida.faltam());
            aplicar.accept(escolhida.vaga(), escolhidos);
            feitos.put(escolhida.id(), new ResultadoVaga<>(escolhida.vaga(), escolhida.id(), escolhida.faltam(), escolhidos));
            restantes.remove(escolhida);
        }
        return pedidos.stream().map(pedido -> feitos.get(pedido.id())).toList();
    }

    private record Aperto(int elegiveis, int faltam, Long id) {

        int folga() {
            return elegiveis - faltam;
        }

        boolean completavel() {
            return folga() >= 0;
        }
    }

    /** Completável antes de perdida; menor folga; menos elegíveis; menor id. */
    private static final Comparator<Aperto> ORDEM_DE_APERTO = Comparator
            .comparing((Aperto a) -> !a.completavel())
            .thenComparingInt(a -> a.completavel() ? a.folga() : 0)
            .thenComparingInt(Aperto::elegiveis)
            .thenComparing(Aperto::id);
}
