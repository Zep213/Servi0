package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.service.escalacao.CandidatoAvaliado;
import br.com.zep.servio.service.escalacao.SorteioService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
}
