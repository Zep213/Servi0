package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.mapper.CelebracaoMapper;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.EventoRequestDTO;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventoPastoralServiceTest {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    private static final LocalDate HOJE = LocalDate.of(2026, 12, 14);

    private final CelebracaoRepository celebracaoRepository = mock(CelebracaoRepository.class);
    private final ComunidadeRepository comunidadeRepository = mock(ComunidadeRepository.class);
    private final PastoralRepository pastoralRepository = mock(PastoralRepository.class);
    private final AlocacaoRepository alocacaoRepository = mock(AlocacaoRepository.class);
    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final CoberturaAutomaticaService cobertura = mock(CoberturaAutomaticaService.class);
    private final ConfiguracaoPastoralService configuracao = mock(ConfiguracaoPastoralService.class);
    private final PastoraisPermissao permissao = mock(PastoraisPermissao.class);
    private final UsuarioLogado usuarioLogado = mock(UsuarioLogado.class);
    private final AuditLogService auditoria = mock(AuditLogService.class);
    private EventoPastoralService service;
    private Pastoral pascom;
    private Comunidade matriz;

    @BeforeEach
    void setUp() {
        service = new EventoPastoralService(celebracaoRepository, Mappers.getMapper(CelebracaoMapper.class),
                comunidadeRepository, pastoralRepository, alocacaoRepository, usuarioRepository, cobertura,
                configuracao, permissao, usuarioLogado, auditoria,
                Clock.fixed(HOJE.atTime(12, 0).atZone(FUSO).toInstant(), FUSO));
        pascom = new Pastoral();
        pascom.setId(1L);
        matriz = new Comunidade();
        matriz.setId(5L);
        when(usuarioLogado.paroquiaId()).thenReturn(9L);
        when(usuarioLogado.id()).thenReturn(77L);
        when(usuarioRepository.findById(77L)).thenReturn(Optional.of(new Usuario()));
        when(pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(1L, 9L)).thenReturn(Optional.of(pascom));
        when(comunidadeRepository.findByIdAndParoquiaIdAndActiveTrue(5L, 9L)).thenReturn(Optional.of(matriz));
        when(permissao.visivel(1L)).thenReturn(true);
        when(permissao.temPapel(1L, "COORDENADOR")).thenReturn(true);
        when(celebracaoRepository.save(any(Celebracao.class))).thenAnswer(i -> {
            Celebracao c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(100L);
            }
            return c;
        });
    }

    private static EventoRequestDTO pedido(LocalDate data) {
        return new EventoRequestDTO(5L, data, LocalTime.of(15, 0), "  Retiro da Pascom  ", null);
    }

    private Celebracao eventoDaPascom() {
        Celebracao evento = new Celebracao();
        evento.setId(100L);
        evento.setTipo(TipoCelebracao.EVENTO);
        evento.setPastoralOrigem(pascom);
        evento.setParoquiaId(9L);
        evento.setData(HOJE.plusDays(10));
        evento.setHora(LocalTime.of(15, 0));
        when(celebracaoRepository.findByIdAndParoquiaIdAndActiveTrue(100L, 9L)).thenReturn(Optional.of(evento));
        return evento;
    }

    @Test
    void criaEventoDaPastoralComTituloAparadoEVagasSoDelaSeACoberturaEstaLigada() {
        when(configuracao.coberturaAutomaticaLigada(1L)).thenReturn(true);

        var resposta = service.criar(1L, pedido(HOJE.plusDays(5)));

        assertEquals(TipoCelebracao.EVENTO, resposta.tipo());
        assertEquals("Retiro da Pascom", resposta.titulo());
        assertEquals(1L, resposta.pastoralOrigemId());
        verify(cobertura).aplicarFuturas(eq(pascom), any());
        verify(cobertura, never()).aplicarNaCriacao(any(), anyLong());
        verify(auditoria).registrar(eq("EVENTO_CRIADO"), eq(100L), any(Usuario.class), eq(9L), any());
    }

    @Test
    void semCoberturaAutomaticaLigadaNaoCriaVaga() {
        when(configuracao.coberturaAutomaticaLigada(1L)).thenReturn(false);

        service.criar(1L, pedido(HOJE));

        verify(cobertura, never()).aplicarFuturas(any(), any());
    }

    @Test
    void naoCoordenadorRecebe403EPastoralInvisivelRecebe404() {
        when(permissao.temPapel(1L, "COORDENADOR")).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.criar(1L, pedido(HOJE.plusDays(5))));

        when(permissao.visivel(1L)).thenReturn(false);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.criar(1L, pedido(HOJE.plusDays(5))));
        verify(celebracaoRepository, never()).save(any());
    }

    @Test
    void dataNoPassadoDa422() {
        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> service.criar(1L, pedido(HOJE.minusDays(1))));
        assertEquals("A data do evento já passou", erro.getMessage());
    }

    @Test
    void comunidadeDeOutraParoquiaDa404() {
        when(comunidadeRepository.findByIdAndParoquiaIdAndActiveTrue(5L, 9L)).thenReturn(Optional.empty());
        assertThrows(RecursoNaoEncontradoException.class, () -> service.criar(1L, pedido(HOJE.plusDays(5))));
    }

    @Test
    void editaEventoSemEscalados() {
        Celebracao evento = eventoDaPascom();
        when(alocacaoRepository.existsByVagaCelebracaoIdAndActiveTrueAndStatusIn(eq(100L), anyCollection()))
                .thenReturn(false);

        var resposta = service.atualizar(1L, 100L, pedido(HOJE.plusDays(20)));

        assertEquals(HOJE.plusDays(20), evento.getData());
        assertEquals("Retiro da Pascom", resposta.titulo());
        assertEquals(TipoCelebracao.EVENTO, evento.getTipo());
        assertEquals(1L, evento.getPastoralOrigem().getId());
    }

    @Test
    void eventoComConviteOuAceiteNaoEditaNemCancela() {
        Celebracao evento = eventoDaPascom();
        when(alocacaoRepository.existsByVagaCelebracaoIdAndActiveTrueAndStatusIn(100L, StatusConvite.OCUPANTES))
                .thenReturn(true);

        RegraNegocioException erro = assertThrows(RegraNegocioException.class,
                () -> service.atualizar(1L, 100L, pedido(HOJE.plusDays(20))));
        assertEquals("Há pessoas escaladas neste evento: peça ao padre", erro.getMessage());
        assertThrows(RegraNegocioException.class, () -> service.desativar(1L, 100L));
        assertTrue(evento.isActive());
        assertEquals(HOJE.plusDays(10), evento.getData());
    }

    @Test
    void cancelaEventoSemEscaladosEAudita() {
        Celebracao evento = eventoDaPascom();

        service.desativar(1L, 100L);

        assertFalse(evento.isActive());
        verify(auditoria).registrar(eq("EVENTO_CANCELADO"), eq(100L), any(Usuario.class), eq(9L), any());
    }

    @Test
    void eventoDeOutraPastoralOuDoPadreOuMissaDa404() {
        Pastoral ecc = new Pastoral();
        ecc.setId(2L);
        Celebracao daOutra = eventoDaPascom();
        daOutra.setPastoralOrigem(ecc);
        assertThrows(RecursoNaoEncontradoException.class, () -> service.desativar(1L, 100L));

        daOutra.setPastoralOrigem(null);                       // criado pelo padre
        assertThrows(RecursoNaoEncontradoException.class, () -> service.atualizar(1L, 100L, pedido(HOJE.plusDays(5))));

        daOutra.setPastoralOrigem(pascom);
        daOutra.setTipo(TipoCelebracao.MISSA_DOMINICAL);       // missa não é evento
        assertThrows(RecursoNaoEncontradoException.class, () -> service.desativar(1L, 100L));
    }
}
