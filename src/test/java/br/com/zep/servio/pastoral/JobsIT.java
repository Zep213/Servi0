package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.PastoralConfig;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralConfigRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import br.com.zep.servio.service.escalacao.TokenConvite;
import br.com.zep.servio.service.escalacao.job.ExpiracaoConvitesJob;
import br.com.zep.servio.service.escalacao.job.LembretesJob;
import br.com.zep.servio.service.notification.Notificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static br.com.zep.servio.pastoral.CenarioEscalacao.domingo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Jobs da escalação (Etapa 6, Parte 5). Relógio fixo (Clock trocado por um de teste), jobs ligados
 * só nesta classe. Sem {@code @Transactional}: a expiração e o envio precisam comitar de verdade.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "servio.jobs.enabled=true")
class JobsIT {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    /** Relógio que a classe controla: começa agora e o teste avança quando precisa. */
    static final RelogioDeTeste RELOGIO = new RelogioDeTeste(Instant.now());

    @TestConfiguration
    static class ConfigRelogio {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return RELOGIO;
        }
    }

    static final class RelogioDeTeste extends Clock {
        private volatile Instant instante;

        RelogioDeTeste(Instant instante) {
            this.instante = instante;
        }

        @Override
        public ZoneId getZone() {
            return FUSO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }

        LocalDateTime agora() {
            return LocalDateTime.ofInstant(instante, FUSO);
        }
    }

    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PastoralRepository pastoralRepository;
    @Autowired
    FuncaoRepository funcaoRepository;
    @Autowired
    UsuarioPastoralRepository usuarioPastoralRepository;
    @Autowired
    ComunidadeRepository comunidadeRepository;
    @Autowired
    CelebracaoRepository celebracaoRepository;
    @Autowired
    VagaRepository vagaRepository;
    @Autowired
    AlocacaoRepository alocacaoRepository;
    @Autowired
    PastoralConfigRepository pastoralConfigRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    TokenConvite tokenConvite;
    @Autowired
    ExpiracaoConvitesJob expiracaoJob;
    @Autowired
    LembretesJob lembretesJob;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Celebracao celebracao;
    Vaga vaga;

    @BeforeEach
    void setUp() {
        RELOGIO.instante = Instant.now();
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        Comunidade comunidade = escala.comunidade(cenario.paroquiaA);
        celebracao = escala.celebracao(cenario.paroquiaA, comunidade, domingo(6), TipoCelebracao.MISSA_DOMINICAL);
        celebracao.setTitulo("Celebração " + UUID.randomUUID());
        celebracaoRepository.save(celebracao);
        vaga = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 5);
    }

    /** Convite pendente com prazo escolhido, já com token (o job e o lembrete trabalham sobre o hash). */
    private Alocacao convite(Vaga vagaDaEscala, br.com.zep.servio.model.Usuario usuario,
                             StatusConvite status, LocalDateTime prazo) {
        Alocacao alocacao = escala.alocacao(cenario.paroquiaA, vagaDaEscala, usuario, status);
        alocacao.setDataLimiteResposta(prazo);
        alocacao.setTokenHash(tokenConvite.hash("token-" + alocacao.getId() + "-" + UUID.randomUUID()));
        return alocacaoRepository.save(alocacao);
    }

    @Test
    void expiraSoOsVencidosEAvisaOCoordenadorUmaVezMesmoRodandoDuasVezes() {
        LocalDateTime agora = RELOGIO.agora();
        Alocacao vencida = convite(vaga, cenario.membro1Pascom, StatusConvite.PENDENTE, agora.minusHours(1));
        Alocacao noPrazo = convite(vaga, cenario.membro2Pascom, StatusConvite.PENDENTE, agora.plusHours(30));
        Alocacao aceita = convite(vaga, cenario.membroEcc, StatusConvite.ACEITA, agora.minusHours(1));

        expiracaoJob.executar();
        expiracaoJob.executar();

        assertThat(alocacaoRepository.findById(vencida.getId()).orElseThrow().getStatus()).isEqualTo(StatusConvite.EXPIRADA);
        assertThat(alocacaoRepository.findById(vencida.getId()).orElseThrow().getTokenHash()).isNull();
        assertThat(alocacaoRepository.findById(noPrazo.getId()).orElseThrow().getStatus()).isEqualTo(StatusConvite.PENDENTE);
        assertThat(alocacaoRepository.findById(aceita.getId()).orElseThrow().getStatus()).isEqualTo(StatusConvite.ACEITA);

        verify(notificador, timeout(5000).times(1)).enviar(eq(cenario.coordenadorPascom.getEmail()), anyString(),
                eq("aviso-coordenador"), any());
        verify(notificador, after(1500).times(1)).enviar(eq(cenario.coordenadorPascom.getEmail()), anyString(),
                eq("aviso-coordenador"), any());
    }

    @Test
    void lembreteDeRespostaSegueAConfigDaPastoralETrocaOToken() {
        LocalDateTime agora = RELOGIO.agora();
        // Pascom usa o padrão (6h antes do prazo): convite a 5h do prazo recebe lembrete
        Alocacao perto = convite(vaga, cenario.membro1Pascom, StatusConvite.PENDENTE, agora.plusHours(5));
        String hashAntes = perto.getTokenHash();

        // ECC configura 2h: o mesmo prazo de 5h ainda não entra na janela
        PastoralConfig config = new PastoralConfig();
        config.setParoquiaId(cenario.paroquiaA.getId());
        config.setPastoral(cenario.ecc);
        config.setChave("LEMBRETE_RESPOSTA");
        config.setParametros(Map.of("horasAntesDoPrazo", 2));
        pastoralConfigRepository.save(config);
        Vaga vagaEcc = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoBarracaEcc, 3);
        convite(vagaEcc, cenario.membroEcc, StatusConvite.PENDENTE, agora.plusHours(5));

        lembretesJob.executar();
        lembretesJob.executar();

        ArgumentCaptor<Map<String, Object>> vars = capturador();
        verify(notificador, timeout(5000).times(1)).enviar(eq(cenario.membro1Pascom.getEmail()), anyString(),
                eq("lembrete-resposta"), vars.capture());
        verify(notificador, after(1000).times(1)).enviar(eq(cenario.membro1Pascom.getEmail()), anyString(),
                eq("lembrete-resposta"), any());
        verify(notificador, never()).enviar(eq(cenario.membroEcc.getEmail()), anyString(), eq("lembrete-resposta"), any());

        // O link do lembrete leva a um token novo, e é esse token que vale no banco
        String link = (String) vars.getValue().get("link");
        String tokenDoLink = link.substring(link.indexOf('#') + 1);
        Alocacao relida = alocacaoRepository.findById(perto.getId()).orElseThrow();
        assertThat(relida.getTokenHash()).isEqualTo(tokenConvite.hash(tokenDoLink));
        assertThat(relida.getTokenHash()).isNotEqualTo(hashAntes);
    }

    @Test
    void paroquiasNaoSeMisturam() {
        LocalDateTime agora = RELOGIO.agora();
        Funcao funcaoB = new Funcao();
        funcaoB.setNome("Leitor");
        funcaoB.setPastoral(cenario.pastoralB);
        funcaoB.setParoquiaId(cenario.paroquiaB.getId());
        funcaoB = funcaoRepository.save(funcaoB);
        Comunidade comunidadeB = escala.comunidade(cenario.paroquiaB);
        Celebracao celebracaoB = escala.celebracao(cenario.paroquiaB, comunidadeB, domingo(6), TipoCelebracao.MISSA_DOMINICAL);
        Vaga vagaB = escala.vaga(cenario.paroquiaB, celebracaoB, funcaoB, 2);
        Alocacao vencidaB = escala.alocacao(cenario.paroquiaB, vagaB, cenario.membroEcc, StatusConvite.PENDENTE);
        vencidaB.setDataLimiteResposta(agora.minusHours(2));
        vencidaB.setTokenHash(tokenConvite.hash("token-b-" + UUID.randomUUID()));
        alocacaoRepository.save(vencidaB);

        expiracaoJob.executar();

        assertThat(alocacaoRepository.findById(vencidaB.getId()).orElseThrow().getStatus()).isEqualTo(StatusConvite.EXPIRADA);
        verify(notificador, timeout(5000).times(1)).enviar(eq(cenario.coordenadorPastoralB.getEmail()), anyString(),
                eq("aviso-coordenador"), any());
        verify(notificador, after(1000).never()).enviar(eq(cenario.coordenadorPascom.getEmail()), anyString(),
                eq("aviso-coordenador"), any());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> capturador() {
        return ArgumentCaptor.forClass(Map.class);
    }
}
