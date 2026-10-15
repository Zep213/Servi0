package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.service.escalacao.CandidatoAvaliado;
import br.com.zep.servio.service.escalacao.SorteioService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras do sorteio com semente fixa: reproduzível, justo e sem violar a quantidade. */
class SorteioServiceTest {

    private static Usuario usuario(long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private static CandidatoAvaliado elegivel(long id, LocalDate ultimaVez) {
        return new CandidatoAvaliado(usuario(id), List.of(), ultimaVez);
    }

    private static CandidatoAvaliado impedido(long id) {
        return new CandidatoAvaliado(usuario(id), List.of("Intervalo mínimo não respeitado"), null);
    }

    private static List<Long> ids(List<Usuario> usuarios) {
        return usuarios.stream().map(Usuario::getId).toList();
    }

    @Test
    void mesma_semente_devolve_o_mesmo_sorteio() {
        List<CandidatoAvaliado> candidatos = IntStream.rangeClosed(1, 20)
                .mapToObj(i -> elegivel(i, null)).toList();

        List<Usuario> primeiro = new SorteioService(new Random(42)).escolher(candidatos, 5);
        List<Usuario> segundo = new SorteioService(new Random(42)).escolher(candidatos, 5);

        assertThat(ids(primeiro)).isEqualTo(ids(segundo));
    }

    @Test
    void quem_nunca_serviu_vem_antes_de_quem_ja_serviu() {
        List<CandidatoAvaliado> candidatos = List.of(
                elegivel(1, LocalDate.of(2026, 9, 1)),
                elegivel(2, null),
                elegivel(3, LocalDate.of(2026, 8, 1)));

        for (long semente = 0; semente < 100; semente++) {
            List<Usuario> escolhidos = new SorteioService(new Random(semente)).escolher(candidatos, 1);
            assertThat(ids(escolhidos)).as("semente %d", semente).containsExactly(2L);
        }
    }

    @Test
    void entre_quem_ja_serviu_vale_a_data_mais_antiga() {
        List<CandidatoAvaliado> candidatos = List.of(
                elegivel(1, LocalDate.of(2026, 9, 1)),
                elegivel(2, LocalDate.of(2026, 8, 1)),
                elegivel(3, LocalDate.of(2026, 7, 1)));

        for (long semente = 0; semente < 50; semente++) {
            List<Usuario> escolhidos = new SorteioService(new Random(semente)).escolher(candidatos, 2);
            assertThat(ids(escolhidos)).as("semente %d", semente).containsExactly(3L, 2L);
        }
    }

    @Test
    void empate_entre_quem_nunca_serviu_e_decidido_pelo_embaralhamento() {
        List<CandidatoAvaliado> candidatos = List.of(elegivel(1, null), elegivel(2, null),
                elegivel(3, null), elegivel(4, null));

        // SplittableRandom e não Random: os primeiros sorteios do java.util.Random com sementes
        // consecutivas saem correlacionados (o índice 0 e o 3 nunca aparecem). Em produção é o SecureRandom.
        Set<Long> primeiros = IntStream.range(0, 200)
                .mapToObj(semente -> new SorteioService(new SplittableRandom(semente)).escolher(candidatos, 1).get(0).getId())
                .collect(Collectors.toSet());

        assertThat(primeiros).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void nunca_passa_da_quantidade() {
        List<CandidatoAvaliado> candidatos = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> elegivel(i, null)).toList();

        assertThat(new SorteioService(new Random(1)).escolher(candidatos, 3)).hasSize(3);
        assertThat(new SorteioService(new Random(1)).escolher(candidatos, 0)).isEmpty();
        assertThat(new SorteioService(new Random(1)).escolher(candidatos, -2)).isEmpty();
    }

    @Test
    void quantidade_maior_que_os_elegiveis_devolve_todos_os_elegiveis() {
        List<CandidatoAvaliado> candidatos = List.of(elegivel(1, null), elegivel(2, null));

        List<Usuario> escolhidos = new SorteioService(new Random(7)).escolher(candidatos, 5);

        assertThat(ids(escolhidos)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void so_sorteia_elegiveis() {
        List<CandidatoAvaliado> candidatos = new ArrayList<>();
        candidatos.add(impedido(1));
        candidatos.add(elegivel(2, null));
        candidatos.add(impedido(3));

        List<Usuario> escolhidos = new SorteioService(new Random(3)).escolher(candidatos, 3);

        assertThat(ids(escolhidos)).containsExactly(2L);
    }

    // ------------------------------------------------ ordem de aperto (função mais apertada primeiro)

    /**
     * Celebração de teste: cada vaga tem as pessoas que sabem fazer a função (habilidade). Quem já foi escolhido numa
     * vaga some das outras, como faz a regra "uma vaga por celebração" depois que a escolha é gravada.
     */
    private static final class Celebracao {

        final Map<Long, Set<Long>> habilidades = new HashMap<>();
        final Set<Long> usados = new HashSet<>();
        final List<Long> ordemDeSorteio = new ArrayList<>();
        final List<SorteioService.PedidoVaga<Long>> pedidos = new ArrayList<>();

        Celebracao vaga(long id, int faltam, Long... pessoas) {
            habilidades.put(id, new LinkedHashSet<>(List.of(pessoas)));
            pedidos.add(new SorteioService.PedidoVaga<>(id, id, faltam));
            return this;
        }

        List<CandidatoAvaliado> candidatos(Long vagaId) {
            return habilidades.get(vagaId).stream()
                    .filter(pessoa -> !usados.contains(pessoa))
                    .map(pessoa -> elegivel(pessoa, null))
                    .toList();
        }

        List<SorteioService.ResultadoVaga<Long>> sortear(SorteioService servico) {
            return servico.sortearPorAperto(pedidos, this::candidatos, (vagaId, escolhidos) -> {
                ordemDeSorteio.add(vagaId);
                escolhidos.forEach(u -> usados.add(u.getId()));
            });
        }
    }

    /** O exemplo do doc: Leitura (id 10) tem 3 candidatos, Som (id 11) só 1, e quem sabe som também sabe ler. */
    @Test
    void a_pessoa_rara_vai_para_a_vaga_apertada_e_a_leitura_fica_com_outro() {
        for (long semente = 0; semente < 200; semente++) {
            Celebracao celebracao = new Celebracao()
                    .vaga(10, 1, 1L, 2L, 3L)      // Leitura: a pessoa 1 (rara) e mais duas
                    .vaga(11, 1, 1L);             // Som: só a pessoa 1

            List<SorteioService.ResultadoVaga<Long>> resultados =
                    celebracao.sortear(new SorteioService(new Random(semente)));

            assertThat(celebracao.ordemDeSorteio).as("semente %d", semente).containsExactly(11L, 10L);
            assertThat(ids(resultados.get(1).escolhidos())).as("Som, semente %d", semente).containsExactly(1L);
            assertThat(ids(resultados.get(0).escolhidos())).as("Leitura, semente %d", semente)
                    .hasSize(1).isSubsetOf(2L, 3L);
            assertThat(resultados).allSatisfy(r -> assertThat(r.restam()).isZero());
        }
    }

    @Test
    void o_resultado_volta_na_ordem_dos_pedidos_e_nao_na_ordem_do_sorteio() {
        Celebracao celebracao = new Celebracao().vaga(10, 1, 1L, 2L, 3L).vaga(11, 1, 1L);

        List<SorteioService.ResultadoVaga<Long>> resultados = celebracao.sortear(new SorteioService(new Random(5)));

        assertThat(resultados).extracting(SorteioService.ResultadoVaga::id).containsExactly(10L, 11L);
        assertThat(celebracao.ordemDeSorteio).containsExactly(11L, 10L);
    }

    @Test
    void menor_folga_vai_primeiro() {
        Celebracao celebracao = new Celebracao()
                .vaga(1, 1, 1L, 2L, 3L)       // folga 2
                .vaga(2, 1, 4L, 5L);          // folga 1

        celebracao.sortear(new SorteioService(new Random(1)));

        assertThat(celebracao.ordemDeSorteio).containsExactly(2L, 1L);
    }

    @Test
    void com_a_mesma_folga_vai_primeiro_quem_tem_menos_elegiveis() {
        Celebracao celebracao = new Celebracao()
                .vaga(1, 2, 1L, 2L, 3L)       // folga 1, 3 elegíveis
                .vaga(2, 1, 4L, 5L);          // folga 1, 2 elegíveis

        celebracao.sortear(new SorteioService(new Random(1)));

        assertThat(celebracao.ordemDeSorteio).containsExactly(2L, 1L);
    }

    @Test
    void empate_total_vai_pelo_menor_id() {
        Celebracao celebracao = new Celebracao()
                .vaga(3, 1, 1L, 2L)
                .vaga(2, 1, 3L, 4L);

        celebracao.sortear(new SorteioService(new Random(1)));

        assertThat(celebracao.ordemDeSorteio).containsExactly(2L, 3L);
    }

    @Test
    void vaga_sem_ninguem_elegivel_nao_impede_as_outras() {
        Celebracao celebracao = new Celebracao()
                .vaga(1, 2)                   // ninguém sabe a função
                .vaga(2, 1, 7L, 8L);

        List<SorteioService.ResultadoVaga<Long>> resultados = celebracao.sortear(new SorteioService(new Random(1)));

        assertThat(resultados.get(0).escolhidos()).isEmpty();
        assertThat(resultados.get(0).restam()).isEqualTo(2);
        assertThat(resultados.get(1).escolhidos()).hasSize(1);
        assertThat(resultados.get(1).restam()).isZero();
    }

    /** Vaga que já é caso perdido (folga negativa) não gasta quem ainda completa outra vaga. */
    @Test
    void vaga_perdida_nao_gasta_a_pessoa_que_completa_outra() {
        for (long semente = 0; semente < 100; semente++) {
            Celebracao celebracao = new Celebracao()
                    .vaga(1, 3, 1L, 2L)       // faltam 3, só 2 sabem: já é perdida (folga -1)
                    .vaga(2, 1, 1L);          // só a pessoa 1 completa esta

            List<SorteioService.ResultadoVaga<Long>> resultados =
                    celebracao.sortear(new SorteioService(new Random(semente)));

            assertThat(celebracao.ordemDeSorteio).as("semente %d", semente).containsExactly(2L, 1L);
            assertThat(ids(resultados.get(1).escolhidos())).containsExactly(1L);
            assertThat(ids(resultados.get(0).escolhidos())).containsExactly(2L);
            assertThat(resultados.get(0).restam()).isEqualTo(2);
        }
    }

    @Test
    void impedidos_nao_contam_como_elegiveis_no_aperto() {
        Celebracao celebracao = new Celebracao().vaga(1, 1, 1L, 2L).vaga(2, 1, 3L, 4L);
        // a vaga 1 tem 2 pessoas, mas uma está impedida: vira 1 elegível (folga 0) e passa na frente da vaga 2
        SorteioService servico = new SorteioService(new Random(1));

        List<SorteioService.ResultadoVaga<Long>> resultados = servico.sortearPorAperto(celebracao.pedidos,
                vagaId -> vagaId == 1L ? List.of(impedido(1), elegivel(2, null)) : celebracao.candidatos(vagaId),
                (vagaId, escolhidos) -> celebracao.ordemDeSorteio.add(vagaId));

        assertThat(celebracao.ordemDeSorteio).containsExactly(1L, 2L);
        assertThat(ids(resultados.get(0).escolhidos())).containsExactly(2L);
    }

    @Test
    void mesma_semente_devolve_o_mesmo_sorteio_das_vagas() {
        List<List<Long>> rodadas = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Celebracao celebracao = new Celebracao()
                    .vaga(1, 2, 1L, 2L, 3L, 4L)
                    .vaga(2, 2, 3L, 4L, 5L, 6L, 7L)
                    .vaga(3, 1, 1L, 7L);
            List<Long> escolhidos = new ArrayList<>();
            celebracao.sortear(new SorteioService(new Random(99)))
                    .forEach(r -> escolhidos.addAll(ids(r.escolhidos())));
            rodadas.add(escolhidos);
        }

        assertThat(rodadas.get(0)).isEqualTo(rodadas.get(1));
    }

    @Test
    void ninguem_serve_em_duas_vagas_e_nenhuma_passa_da_quantidade() {
        for (long semente = 0; semente < 100; semente++) {
            Celebracao celebracao = new Celebracao()
                    .vaga(1, 3, 1L, 2L, 3L, 4L, 5L)
                    .vaga(2, 2, 3L, 4L, 5L, 6L)
                    .vaga(3, 2, 1L, 6L, 7L);

            List<SorteioService.ResultadoVaga<Long>> resultados =
                    celebracao.sortear(new SorteioService(new Random(semente)));

            List<Long> todos = resultados.stream().flatMap(r -> ids(r.escolhidos()).stream()).toList();
            assertThat(todos).as("semente %d", semente).doesNotHaveDuplicates();
            assertThat(resultados).allSatisfy(r -> assertThat(r.escolhidos().size()).isLessThanOrEqualTo(r.faltam()));
        }
    }
}
