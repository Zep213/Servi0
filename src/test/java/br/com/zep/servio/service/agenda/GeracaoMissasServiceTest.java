package br.com.zep.servio.service.agenda;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.HorarioMissa;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.service.CoberturaAutomaticaService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GeracaoMissasServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    /** Segunda-feira, 14/12/2026 às 12:00. */
    private static final LocalDateTime SEGUNDA = LocalDateTime.of(2026, 12, 14, 12, 0);

    @Test
    void oitoSemanasAFrenteDaOitoDomingosDe20_12A07_02() {
        List<LocalDate> datas = GeracaoMissasService.datasAGerar(DayOfWeek.SUNDAY, LocalTime.of(10, 0), SEGUNDA, 8);

        assertEquals(8, datas.size());
        assertEquals(LocalDate.of(2026, 12, 20), datas.get(0));
        assertEquals(LocalDate.of(2027, 2, 7), datas.get(7));
        assertTrue(datas.stream().allMatch(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY));
    }

    @Test
    void janeiroEstaNaJanela() {
        List<LocalDate> datas = GeracaoMissasService.datasAGerar(DayOfWeek.SUNDAY, LocalTime.of(10, 0), SEGUNDA, 8);

        assertTrue(datas.containsAll(List.of(LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 10),
                LocalDate.of(2027, 1, 17), LocalDate.of(2027, 1, 24), LocalDate.of(2027, 1, 31))));
    }

    @Test
    void hojeSoEntraSeAHoraDaMissaAindaNaoPassou() {
        LocalDateTime domingoDeManha = LocalDateTime.of(2026, 12, 20, 8, 0);
        LocalDateTime domingoDepois = LocalDateTime.of(2026, 12, 20, 11, 0);

        assertEquals(LocalDate.of(2026, 12, 20),
                GeracaoMissasService.datasAGerar(DayOfWeek.SUNDAY, LocalTime.of(10, 0), domingoDeManha, 8).get(0));
        assertEquals(LocalDate.of(2026, 12, 27),
                GeracaoMissasService.datasAGerar(DayOfWeek.SUNDAY, LocalTime.of(10, 0), domingoDepois, 8).get(0));
    }

    @Test
    void outroDiaDaSemanaEPrimeiraDataNaoPassaDeHoje() {
        List<LocalDate> sabados = GeracaoMissasService.datasAGerar(DayOfWeek.SATURDAY, LocalTime.of(19, 0), SEGUNDA, 2);

        assertEquals(List.of(LocalDate.of(2026, 12, 19), LocalDate.of(2026, 12, 26)), sabados);
    }

    @Test
    void semanasZeroSoPegaOQueFicaAteHoje() {
        assertTrue(GeracaoMissasService.datasAGerar(DayOfWeek.SUNDAY, LocalTime.of(10, 0), SEGUNDA, 0).isEmpty());
    }

    private HorarioMissa horario(boolean ativo, boolean comunidadeAtiva) {
        Comunidade comunidade = new Comunidade();
        comunidade.setId(3L);
        comunidade.setActive(comunidadeAtiva);
        HorarioMissa horario = new HorarioMissa();
        horario.setId(7L);
        horario.setComunidade(comunidade);
        horario.setHora(LocalTime.of(10, 0));
        horario.setParoquiaId(1L);
        horario.setActive(ativo);
        return horario;
    }

    private GeracaoMissasService servico(CelebracaoRepository repository, CoberturaAutomaticaService cobertura) {
        return new GeracaoMissasService(repository, cobertura, Clock.fixed(SEGUNDA.atZone(FUSO).toInstant(), FUSO), 8);
    }

    @Test
    void geraSoAsDatasQueNaoExistemEChamaAmCoberturaDeCadaUma() {
        CelebracaoRepository repository = mock(CelebracaoRepository.class);
        CoberturaAutomaticaService cobertura = mock(CoberturaAutomaticaService.class);
        // 03/01 já existe (inclusive se estiver desativada): não é recriada
        when(repository.existsByHorarioMissaIdAndData(7L, LocalDate.of(2027, 1, 3))).thenReturn(true);

        int criadas = servico(repository, cobertura).gerar(horario(true, true));

        assertEquals(7, criadas);
        verify(repository, times(7)).save(any(Celebracao.class));
        verify(cobertura, times(7)).aplicarNaCriacao(any(Celebracao.class), anyLong());
    }

    @Test
    void horarioOuComunidadeInativosNaoGeram() {
        CelebracaoRepository repository = mock(CelebracaoRepository.class);
        CoberturaAutomaticaService cobertura = mock(CoberturaAutomaticaService.class);
        GeracaoMissasService servico = servico(repository, cobertura);

        assertEquals(0, servico.gerar(horario(false, true)));
        assertEquals(0, servico.gerar(horario(true, false)));
        verify(repository, never()).save(any());
    }
}
