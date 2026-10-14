package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.service.escalacao.regra.ContextoElegibilidade;
import br.com.zep.servio.service.escalacao.regra.IntervaloMinimoRegra;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntervaloMinimoRegraTest {

    @Mock
    AlocacaoRepository alocacaoRepository;

    @Test
    void codigoEPadraoDe48Horas() {
        IntervaloMinimoRegra r = regra();
        assertThat(r.codigo()).isEqualTo("INTERVALO_MINIMO");
        assertThat(r.padroes()).containsEntry("horas", 48L);
    }

    @Test
    void bloqueiaComMenosDe48HorasNoPadrao() {
        var vagaExistente = vagaEm(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0));
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(anyLong(), any(), anyLong()))
                .thenReturn(List.of(alocacao(vagaExistente)));

        var contexto = contexto(LocalDate.of(2026, 9, 22), LocalTime.of(9, 0)); // 47h depois

        var resultado = regra().impedimento(candidato(), contexto, Map.of());

        assertThat(resultado).isPresent();
    }

    @Test
    void naoBloqueiaComExatos48HorasNoPadrao() {
        var vagaExistente = vagaEm(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0));
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(anyLong(), any(), anyLong()))
                .thenReturn(List.of(alocacao(vagaExistente)));

        var contexto = contexto(LocalDate.of(2026, 9, 22), LocalTime.of(10, 0)); // exatos 48h

        var resultado = regra().impedimento(candidato(), contexto, Map.of());

        assertThat(resultado).isEmpty();
    }

    @Test
    void parametroSobrescritoAmpliaOIntervalo() {
        var vagaExistente = vagaEm(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0));
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(anyLong(), any(), anyLong()))
                .thenReturn(List.of(alocacao(vagaExistente)));

        var contexto = contexto(LocalDate.of(2026, 9, 22), LocalTime.of(10, 0)); // 48h depois

        var resultado = regra().impedimento(candidato(), contexto, Map.of("horas", 72));

        assertThat(resultado).isPresent();
    }

    @Test
    void parametroComoStringTambemFunciona() {
        var vagaExistente = vagaEm(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0));
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(anyLong(), any(), anyLong()))
                .thenReturn(List.of(alocacao(vagaExistente)));

        var contexto = contexto(LocalDate.of(2026, 9, 20), LocalTime.of(11, 0)); // 1h depois

        var resultado = regra().impedimento(candidato(), contexto, Map.of("horas", "2"));

        assertThat(resultado).isPresent();
    }

    @Test
    void semAlocacoesExistentesNaoBloqueia() {
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(anyLong(), any(), anyLong()))
                .thenReturn(List.of());

        var resultado = regra().impedimento(candidato(), contexto(LocalDate.of(2026, 9, 20), LocalTime.of(10, 0)), Map.of());

        assertThat(resultado).isEmpty();
    }

    private IntervaloMinimoRegra regra() {
        return new IntervaloMinimoRegra(alocacaoRepository);
    }

    private Usuario candidato() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        return usuario;
    }

    private Vaga vagaEm(LocalDate data, LocalTime hora) {
        Celebracao celebracao = new Celebracao();
        celebracao.setData(data);
        celebracao.setHora(hora);
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        return vaga;
    }

    private ContextoElegibilidade contexto(LocalDate data, LocalTime hora) {
        return new ContextoElegibilidade(vagaEm(data, hora), 0L);
    }

    private Alocacao alocacao(Vaga vaga) {
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        return alocacao;
    }
}
