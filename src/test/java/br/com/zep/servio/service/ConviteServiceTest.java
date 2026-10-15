package br.com.zep.servio.service;

import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.NotificacaoEnviadaRepository;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import br.com.zep.servio.service.escalacao.ConviteService;
import br.com.zep.servio.service.escalacao.TokenConvite;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConviteServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    /** Sexta, 18/12/2026 às 18:00 no fuso da paróquia. */
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 12, 18, 18, 0);

    private final ConfiguracaoPastoralService configuracao = mock(ConfiguracaoPastoralService.class);
    private final AlocacaoRepository alocacaoRepository = mock(AlocacaoRepository.class);
    private final Clock clock = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);

    private ConviteService servico(long margemMinutos) {
        when(configuracao.prazoRespostaHoras(anyLong())).thenReturn(24L);
        return new ConviteService(new TokenConvite(), configuracao, mock(NotificacaoEnviadaRepository.class),
                alocacaoRepository, mock(ApplicationEventPublisher.class), clock, margemMinutos);
    }

    private static Celebracao celebracao(LocalDateTime inicio) {
        Celebracao celebracao = new Celebracao();
        celebracao.setData(inicio.toLocalDate());
        celebracao.setHora(inicio.toLocalTime());
        celebracao.setId(10L);
        return celebracao;
    }

    @Test
    void celebracaoDistanteMantemOPrazoDaPastoral() {
        assertEquals(AGORA.plusHours(24), servico(0).novoPrazo(1L, celebracao(AGORA.plusDays(3))));
    }

    @Test
    void celebracaoNoDiaSeguinteCedoLimitaAHoraDaMissa() {
        LocalDateTime missa = LocalDateTime.of(2026, 12, 19, 10, 0);
        assertEquals(missa, servico(0).novoPrazo(1L, celebracao(missa)));
    }

    @Test
    void celebracaoDaquiAMeiaHoraTemPrazoDeMeiaHora() {
        assertEquals(AGORA.plusMinutes(30), servico(0).novoPrazo(1L, celebracao(AGORA.plusMinutes(30))));
    }

    @Test
    void margemDeUmaHoraTiraUmaHoraDoLimite() {
        LocalDateTime missa = LocalDateTime.of(2026, 12, 19, 10, 0);
        assertEquals(missa.minusHours(1), servico(60).novoPrazo(1L, celebracao(missa)));
    }

    @Test
    void celebracaoQueJaComecouOuNoLimiteDa422() {
        ConviteService servico = servico(0);
        assertThrows(RegraNegocioException.class, () -> servico.novoPrazo(1L, celebracao(AGORA.minusMinutes(1))));
        assertThrows(RegraNegocioException.class, () -> servico.novoPrazo(1L, celebracao(AGORA)));
        // com margem de 60 minutos, uma missa daqui a 30 minutos já passou do limite
        ConviteService comMargem = servico(60);
        assertThrows(RegraNegocioException.class, () -> comMargem.novoPrazo(1L, celebracao(AGORA.plusMinutes(30))));
    }

    @Test
    void invarianteOPrazoNuncaPassaDoInicioDaCelebracao() {
        ConviteService servico = servico(0);
        for (long minutos : List.of(1L, 59L, 60L, 600L, 1439L, 1440L, 1441L, 5000L, 100_000L)) {
            Celebracao celebracao = celebracao(AGORA.plusMinutes(minutos));
            LocalDateTime inicio = celebracao.getData().atTime(celebracao.getHora());
            LocalDateTime prazo = servico.novoPrazo(1L, celebracao);
            assertEquals(true, !prazo.isAfter(inicio), "a " + minutos + " minutos da missa");
        }
    }

    private Alocacao pendente(LocalDateTime prazo) {
        Alocacao alocacao = new Alocacao();
        alocacao.setStatus(StatusConvite.PENDENTE);
        alocacao.setDataLimiteResposta(prazo);
        return alocacao;
    }

    @Test
    void ajustarPrazosSoEncurtaENuncaEstende() {
        ConviteService servico = servico(0);
        LocalDateTime novoInicio = AGORA.plusHours(5);
        Alocacao passaDoLimite = pendente(AGORA.plusHours(20));
        Alocacao jaAntes = pendente(AGORA.plusHours(2));
        when(alocacaoRepository.pendentesDaCelebracao(10L, StatusConvite.PENDENTE))
                .thenReturn(List.of(passaDoLimite, jaAntes));

        servico.ajustarPrazosPendentes(celebracao(novoInicio));

        assertEquals(novoInicio, passaDoLimite.getDataLimiteResposta());
        assertEquals(AGORA.plusHours(2), jaAntes.getDataLimiteResposta());
        verify(alocacaoRepository).save(passaDoLimite);
        verify(alocacaoRepository, never()).save(jaAntes);
    }

    @Test
    void relogioSemAlocacaoPendenteNaoSalvaNada() {
        ConviteService servico = servico(0);
        when(alocacaoRepository.pendentesDaCelebracao(10L, StatusConvite.PENDENTE)).thenReturn(List.of());
        servico.ajustarPrazosPendentes(celebracao(AGORA.plusHours(1)));
        verify(alocacaoRepository, never()).save(any());
    }
}
