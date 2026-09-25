package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.service.escalacao.regra.ContextoElegibilidade;
import br.com.zep.servio.service.escalacao.regra.DomingosSeguidosRegra;
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
class DomingosSeguidosRegraTest {

    @Mock
    AlocacaoRepository alocacaoRepository;

    @Test
    void codigoEPadroes() {
        assertThat(regra().codigo()).isEqualTo("DOMINGOS_SEGUIDOS");
        assertThat(regra().padroes()).isEmpty();
    }

    @Test
    void naoBloqueiaQuandoDataNaoEhDomingo() {
        DomingosSeguidosRegra r = regra();
        LocalDate segunda = LocalDate.of(2026, 9, 21); // segunda-feira
        var contexto = contexto(segunda);

        var resultado = r.impedimento(candidato(), contexto, Map.of());

        assertThat(resultado).isEmpty();
    }

    @Test
    void naoBloqueiaSemAlocacoesConflitantes() {
        LocalDate domingo = LocalDate.of(2026, 9, 20);
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                anyLong(), any(), anyLong())).thenReturn(List.of());

        var resultado = regra().impedimento(candidato(), contexto(domingo), Map.of());

        assertThat(resultado).isEmpty();
    }

    @Test
    void bloqueiaQuandoJaEscaladoNoDomingoAnterior() {
        LocalDate domingo = LocalDate.of(2026, 9, 20);
        LocalDate domingoAnterior = domingo.minusDays(7);
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                anyLong(), any(), anyLong())).thenReturn(List.of(alocacaoEm(domingoAnterior)));

        var resultado = regra().impedimento(candidato(), contexto(domingo), Map.of());

        assertThat(resultado).isPresent();
    }

    @Test
    void bloqueiaQuandoJaEscaladoNoDomingoSeguinte() {
        LocalDate domingo = LocalDate.of(2026, 9, 20);
        LocalDate domingoSeguinte = domingo.plusDays(7);
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                anyLong(), any(), anyLong())).thenReturn(List.of(alocacaoEm(domingoSeguinte)));

        var resultado = regra().impedimento(candidato(), contexto(domingo), Map.of());

        assertThat(resultado).isPresent();
    }

    @Test
    void bloqueiaNaViradaDeAno() {
        LocalDate domingo = LocalDate.of(2027, 1, 3); // domingo
        LocalDate domingoAnterior = LocalDate.of(2026, 12, 27); // domingo, ano anterior
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                anyLong(), any(), anyLong())).thenReturn(List.of(alocacaoEm(domingoAnterior)));

        var resultado = regra().impedimento(candidato(), contexto(domingo), Map.of());

        assertThat(resultado).isPresent();
    }

    @Test
    void naoBloqueiaAlocacaoDuasSemanasAntes() {
        LocalDate domingo = LocalDate.of(2026, 9, 20);
        LocalDate duasSemanasAntes = domingo.minusDays(14);
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                anyLong(), any(), anyLong())).thenReturn(List.of(alocacaoEm(duasSemanasAntes)));

        var resultado = regra().impedimento(candidato(), contexto(domingo), Map.of());

        assertThat(resultado).isEmpty();
    }

    private DomingosSeguidosRegra regra() {
        return new DomingosSeguidosRegra(alocacaoRepository);
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

    private Alocacao alocacaoEm(LocalDate data) {
        Celebracao celebracao = new Celebracao();
        celebracao.setData(data);
        celebracao.setHora(LocalTime.of(10, 0));
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        alocacao.setStatus(StatusConvite.ACEITA);
        return alocacao;
    }
}
