package br.com.zep.servio.service;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.HorarioMissa;
import br.com.zep.servio.model.ModeloVaga;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoCelebracaoModelo;
import br.com.zep.servio.repository.CoberturaMissaRepository;
import br.com.zep.servio.repository.ModeloVagaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Regra do cartão 1 da sprint 4: celebração de horário fixo só dá vaga a quem cobre aquele horário. */
class CoberturaAutomaticaServiceTest {

    private final PastoralRepository pastoralRepository = mock(PastoralRepository.class);
    private final ModeloVagaRepository modeloVagaRepository = mock(ModeloVagaRepository.class);
    private final VagaRepository vagaRepository = mock(VagaRepository.class);
    private final ConfiguracaoPastoralService configuracao = mock(ConfiguracaoPastoralService.class);
    private final CoberturaMissaRepository coberturaRepository = mock(CoberturaMissaRepository.class);
    private final CoberturaAutomaticaService service = new CoberturaAutomaticaService(pastoralRepository,
            modeloVagaRepository, vagaRepository, configuracao, coberturaRepository);

    private Pastoral pascom;
    private Pastoral ecc;

    @BeforeEach
    void setUp() {
        pascom = pastoral(1L);
        ecc = pastoral(2L);
        when(pastoralRepository.findByParoquiaIdAndActiveTrue(9L)).thenReturn(List.of(pascom, ecc));
        when(configuracao.coberturaAutomaticaLigada(1L)).thenReturn(true);
        when(configuracao.coberturaAutomaticaLigada(2L)).thenReturn(true);
        when(modeloVagaRepository.findByPastoralIdAndActiveTrue(1L)).thenReturn(List.of(modelo(pascom, 100L)));
        when(modeloVagaRepository.findByPastoralIdAndActiveTrue(2L)).thenReturn(List.of(modelo(ecc, 200L)));
    }

    private static Pastoral pastoral(Long id) {
        Pastoral pastoral = new Pastoral();
        pastoral.setId(id);
        return pastoral;
    }

    private static ModeloVaga modelo(Pastoral pastoral, Long funcaoId) {
        Funcao funcao = new Funcao();
        funcao.setId(funcaoId);
        funcao.setPastoral(pastoral);
        ModeloVaga modelo = new ModeloVaga();
        modelo.setPastoral(pastoral);
        modelo.setFuncao(funcao);
        modelo.setTipoCelebracao(TipoCelebracaoModelo.TODOS);
        modelo.setQuantidade(2);
        return modelo;
    }

    private static Celebracao celebracao(Long id, HorarioMissa horario) {
        Celebracao celebracao = new Celebracao();
        celebracao.setId(id);
        celebracao.setTipo(TipoCelebracao.MISSA_DOMINICAL);
        celebracao.setParoquiaId(9L);
        celebracao.setHorarioMissa(horario);
        return celebracao;
    }

    private static HorarioMissa horario(Long id) {
        HorarioMissa horario = new HorarioMissa();
        horario.setId(id);
        return horario;
    }

    @Test
    void celebracaoCriadaAMaoDaVagaATodasAsPastoraisComCoberturaLigada() {
        service.aplicarNaCriacao(celebracao(10L, null), 9L);

        verify(vagaRepository, times(2)).save(any(Vaga.class));
    }

    @Test
    void celebracaoDeHorarioFixoSoDaVagaAQuemCobreOHorario() {
        HorarioMissa dezHoras = horario(50L);
        when(coberturaRepository.existsByPastoralIdAndHorarioMissaIdAndActiveTrue(1L, 50L)).thenReturn(true);
        when(coberturaRepository.existsByPastoralIdAndHorarioMissaIdAndActiveTrue(2L, 50L)).thenReturn(false);

        service.aplicarNaCriacao(celebracao(10L, dezHoras), 9L);

        verify(vagaRepository, times(1)).save(any(Vaga.class));
        verify(modeloVagaRepository, never()).findByPastoralIdAndActiveTrue(2L);
    }

    @Test
    void pastoralQueNaoEscolheuNenhumHorarioNaoRecebeNadaNasGeradas() {
        service.aplicarNaCriacao(celebracao(10L, horario(50L)), 9L);

        verify(vagaRepository, never()).save(any(Vaga.class));
    }

    @Test
    void aplicarFuturasRespeitaOHorarioDeCadaCelebracao() {
        when(coberturaRepository.existsByPastoralIdAndHorarioMissaIdAndActiveTrue(1L, 50L)).thenReturn(true);
        Celebracao daPascom = celebracao(10L, horario(50L));
        Celebracao deOutroHorario = celebracao(11L, horario(51L));
        Celebracao aMao = celebracao(12L, null);

        int criadas = service.aplicarFuturas(pascom, List.of(daPascom, deOutroHorario, aMao));

        assertEquals(2, criadas);
    }
}
