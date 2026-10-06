package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.escalacao.TokenConvite;
import br.com.zep.servio.service.escalacao.evento.ConviteCriadoEvent;
import br.com.zep.servio.service.notification.Notificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.zep.servio.pastoral.CenarioEscalacao.domingo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * E-mails da escalação (Etapa 6, Parte 4). Sem {@code @Transactional}: o envio acontece depois
 * do commit, então o teste precisa comitar de verdade. Os envios são assíncronos, por isso as
 * verificações esperam com {@code timeout}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@RecordApplicationEvents
class NotificacaoIT {

    private static final AtomicInteger IPS = new AtomicInteger(1);
    private static final long ESPERA_MS = 5000;

    @Autowired
    MockMvc mvc;
    @Autowired
    tools.jackson.databind.ObjectMapper json;
    @Autowired
    ApplicationEvents eventos;
    @Autowired
    ApplicationEventPublisher publicador;
    @Autowired
    PlatformTransactionManager transacoes;
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
    PasswordEncoder passwordEncoder;
    @Autowired
    TokenConvite tokenConvite;
    @Value("${servio.front-url}")
    String frontUrl;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Vaga vaga;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        Comunidade comunidade = escala.comunidade(cenario.paroquiaA);
        Celebracao celebracao = escala.celebracao(cenario.paroquiaA, comunidade, domingo(6), TipoCelebracao.MISSA_DOMINICAL);
        vaga = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 5);
        vaga.setHorarioChegada(LocalTime.of(18, 30));
        vaga.setObservacao("Levar o microfone");
        vagaRepository.save(vaga);
    }

    private Long convidar(br.com.zep.servio.model.Usuario usuario) throws Exception {
        String resposta = mvc.perform(post("/api/alocacoes").contentType("application/json")
                        .content("{\"vagaId\":" + vaga.getId() + ",\"usuarioId\":" + usuario.getId() + "}")
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).get("id").asLong();
    }

    private String tokenDo(Long alocacaoId) {
        return eventos.stream(ConviteCriadoEvent.class)
                .filter(e -> e.alocacaoId().equals(alocacaoId))
                .findFirst().orElseThrow().token();
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Map<String, Object>> capturador() {
        return ArgumentCaptor.forClass(Map.class);
    }

    @Test
    void conviteVaiParaAPessoaDepoisDoCommitComHorarioFuncaoELink() throws Exception {
        Long id = convidar(cenario.membro2Pascom);
        ArgumentCaptor<Map<String, Object>> vars = capturador();

        verify(notificador, timeout(ESPERA_MS)).enviar(eq(cenario.membro2Pascom.getEmail()), anyString(),
                eq("convite"), vars.capture());

        Map<String, Object> v = vars.getValue();
        assertThat(v.get("funcao")).isEqualTo("Comunicação");
        assertThat(v.get("chegada")).isEqualTo("18:30");
        assertThat(v.get("observacao")).isEqualTo("Levar o microfone");
        assertThat(v.get("nome")).isEqualTo("membro2.pascom");
        assertThat((String) v.get("link")).isEqualTo(frontUrl + "/convite#" + tokenDo(id));
    }

    @Test
    void falhaDoEnvioNaoDesfazAAlocacao() throws Exception {
        doThrow(new IllegalStateException("SMTP fora do ar"))
                .when(notificador).enviar(any(), any(), any(), any());

        Long id = convidar(cenario.membro2Pascom);

        verify(notificador, timeout(ESPERA_MS)).enviar(any(), any(), any(), any());
        Alocacao relida = alocacaoRepository.findById(id).orElseThrow();
        assertThat(relida.getStatus()).isEqualTo(StatusConvite.PENDENTE);
        assertThat(relida.getTokenHash()).isNotNull();
    }

    @Test
    void eventoRepetidoEnviaUmaVez() {
        Alocacao alocacao = novaAlocacaoPendente(cenario.membro1Pascom);
        String token = "token-de-teste-" + alocacao.getId();
        alocacao.setTokenHash(tokenConvite.hash(token));
        alocacaoRepository.save(alocacao);

        new TransactionTemplate(transacoes).executeWithoutResult(s -> {
            publicador.publishEvent(new ConviteCriadoEvent(alocacao.getId(), token, TipoNotificacao.CONVITE));
            publicador.publishEvent(new ConviteCriadoEvent(alocacao.getId(), token, TipoNotificacao.CONVITE));
        });

        verify(notificador, timeout(ESPERA_MS)).enviar(eq(cenario.membro1Pascom.getEmail()), anyString(),
                eq("convite"), any());
        verify(notificador, after(1000).times(1)).enviar(eq(cenario.membro1Pascom.getEmail()), anyString(),
                eq("convite"), any());
    }

    @Test
    void semCommitNaoHaEmail() {
        Alocacao alocacao = novaAlocacaoPendente(cenario.membro1Pascom);
        String token = "token-revertido-" + alocacao.getId();
        alocacao.setTokenHash(tokenConvite.hash(token));
        alocacaoRepository.save(alocacao);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new TransactionTemplate(transacoes).executeWithoutResult(s -> {
                    publicador.publishEvent(new ConviteCriadoEvent(alocacao.getId(), token, TipoNotificacao.CONVITE));
                    throw new IllegalStateException("rollback de propósito");
                }));

        verify(notificador, after(1500).never()).enviar(any(), any(), any(), any());
    }

    @Test
    void recusaAvisaOsCoordenadoresComAJustificativa() throws Exception {
        Long id = convidar(cenario.membro2Pascom);
        String token = tokenDo(id);

        mvc.perform(post("/api/confirmacoes/responder").contentType("application/json")
                        .content("{\"token\":\"" + token + "\",\"aceitar\":false,\"justificativa\":\"Vou viajar\"}")
                        .with(csrf())
                        .with(r -> {
                            r.setRemoteAddr("10.9.0." + IPS.getAndIncrement());
                            return r;
                        }))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> vars = capturador();
        verify(notificador, timeout(ESPERA_MS)).enviar(eq(cenario.coordenadorPascom.getEmail()), anyString(),
                eq("aviso-coordenador"), vars.capture());
        assertThat((String) vars.getValue().get("mensagem")).contains("membro2.pascom", "Vou viajar");
    }

    @Test
    void substituicaoAvisaQuemSaiEConvidaQuemEntra() throws Exception {
        Long antiga = convidar(cenario.membro2Pascom);
        verify(notificador, timeout(ESPERA_MS)).enviar(eq(cenario.membro2Pascom.getEmail()), anyString(),
                eq("convite"), any());

        mvc.perform(post("/api/alocacoes/{id}/substituir", antiga).contentType("application/json")
                        .content("{\"usuarioId\":" + cenario.membro1Pascom.getId() + ",\"forcar\":false}")
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isCreated());

        verify(notificador, timeout(ESPERA_MS)).enviar(eq(cenario.membro2Pascom.getEmail()), anyString(),
                eq("substituicao"), any());
        verify(notificador, timeout(ESPERA_MS)).enviar(eq(cenario.membro1Pascom.getEmail()), anyString(),
                eq("convite"), any());
    }

    @Test
    void vagaQueFicouAbertaAvisaOCoordenador() throws Exception {
        Celebracao celebracao = vaga.getCelebracao();
        Vaga grande = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 50);

        mvc.perform(post("/api/vagas/{id}/sortear", grande.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> vars = capturador();
        verify(notificador, timeout(ESPERA_MS)).enviar(eq(cenario.coordenadorPascom.getEmail()), anyString(),
                eq("aviso-coordenador"), vars.capture());
        assertThat((String) vars.getValue().get("mensagem")).startsWith("Faltam ");
    }

    private Alocacao novaAlocacaoPendente(br.com.zep.servio.model.Usuario usuario) {
        Alocacao alocacao = escala.alocacao(cenario.paroquiaA, vaga, usuario, StatusConvite.PENDENTE);
        alocacao.setDataLimiteResposta(java.time.LocalDateTime.now().plusDays(1));
        return alocacao;
    }
}
