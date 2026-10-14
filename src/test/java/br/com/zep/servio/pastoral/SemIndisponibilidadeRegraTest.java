package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Indisponibilidade;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.repository.IndisponibilidadeRepository;
import br.com.zep.servio.service.escalacao.regra.ContextoElegibilidade;
import br.com.zep.servio.service.escalacao.regra.SemIndisponibilidadeRegra;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemIndisponibilidadeRegraTest {

    @Mock
    IndisponibilidadeRepository indisponibilidadeRepository;

    @Test
    void codigoEPadroes() {
        SemIndisponibilidadeRegra r = regra();
        assertThat(r.codigo()).isEqualTo("SEM_INDISPONIBILIDADE");
        assertThat(r.padroes()).isEmpty();
    }

    @Test
    void semIndisponibilidadeNaoBloqueia() {
        LocalDate data = LocalDate.of(2026, 9, 20);
        when(indisponibilidadeRepository.findByUsuarioIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndActiveTrue(1L, data, data))
                .thenReturn(List.of());

        var resultado = regra().impedimento(candidato(), contexto(data), Map.of());

        assertThat(resultado).isEmpty();
    }

    @Test
    void dataIgualAoInicioDaIndisponibilidadeBloqueia() {
        LocalDate data = LocalDate.of(2026, 9, 20);
        when(indisponibilidadeRepository.findByUsuarioIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndActiveTrue(1L, data, data))
                .thenReturn(List.of(new Indisponibilidade()));

        var resultado = regra().impedimento(candidato(), contexto(data), Map.of());

        assertThat(resultado).isPresent();
    }

    @Test
    void dataIgualAoFimDaIndisponibilidadeBloqueia() {
        LocalDate data = LocalDate.of(2026, 9, 25);
        when(indisponibilidadeRepository.findByUsuarioIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndActiveTrue(1L, data, data))
                .thenReturn(List.of(new Indisponibilidade()));

        var resultado = regra().impedimento(candidato(), contexto(data), Map.of());

        assertThat(resultado).isPresent();
    }

    @Test
    void dataForaDoIntervaloNaoBloqueia() {
        LocalDate data = LocalDate.of(2026, 9, 26);
        when(indisponibilidadeRepository.findByUsuarioIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndActiveTrue(1L, data, data))
                .thenReturn(List.of());

        var resultado = regra().impedimento(candidato(), contexto(data), Map.of());

        assertThat(resultado).isEmpty();
    }

    private SemIndisponibilidadeRegra regra() {
        return new SemIndisponibilidadeRegra(indisponibilidadeRepository);
    }

    private Usuario candidato() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        return usuario;
    }

    private ContextoElegibilidade contexto(LocalDate data) {
        Celebracao celebracao = new Celebracao();
        celebracao.setData(data);
        celebracao.setHora(LocalTime.of(10, 0));
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        return new ContextoElegibilidade(vaga, 0L);
    }
}
