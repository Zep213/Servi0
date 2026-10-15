package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.EmailSaida;
import br.com.zep.servio.model.TokenConta;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.StatusEmail;
import br.com.zep.servio.repository.EmailSaidaRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.TokenContaRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.escalacao.TokenConvite;
import br.com.zep.servio.service.notification.Notificador;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro de membro sem senha e ativação pelo link (Sprint 2, Cartão 2). Sem {@code @Transactional}: o
 * e-mail só entra na fila no commit. {@code Notificador} mockado: o link chega pelas variáveis do e-mail.
 * Cada teste usa e-mail e IP próprios (o banco e o limite por IP são compartilhados entre os ITs).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AtivacaoContaIT {

    private static final AtomicInteger IPS = new AtomicInteger();

    @Autowired
    MockMvc mvc;
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
    PasswordEncoder passwordEncoder;
    @Autowired
    TokenContaRepository tokenContaRepository;
    @Autowired
    EmailSaidaRepository emailSaidaRepository;
    @Autowired
    TokenConvite tokenConvite;
    @Autowired
    Clock clock;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
    }

    private static String emailNovo() {
        return "novo." + UUID.randomUUID() + "@teste.dev";
    }

    private static String ipUnico() {
        int n = IPS.getAndIncrement();
        return "10.7." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions cadastrar(Usuario quem, Long pastoralId, String email, String papel) throws Exception {
        String corpo = "{\"nome\":\"Maria da Silva\",\"email\":\"" + email + "\""
                + (papel == null ? "" : ",\"papel\":\"" + papel + "\"") + "}";
        return mvc.perform(post("/api/pastorais/{id}/membros", pastoralId)
                .with(user(cenario.principal(quem))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    private ResultActions publico(String rota, String corpo) throws Exception {
        String ip = ipUnico();
        return mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(corpo).with(csrf())
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                }));
    }

    private ResultActions entrar(String login, String senha) throws Exception {
        String ip = ipUnico();
        return mvc.perform(post("/api/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("login", login).param("senha", senha)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                }));
    }

    /** O token puro só existe no e-mail: pega o link entregue ao Notificador e tira o que vem depois de '#'. */
    private String tokenDoEmail(String email) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> vars = (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(notificador, timeout(5000).atLeastOnce()).enviar(eq(email), anyString(), eq("ativacao"), vars.capture());
        String link = (String) vars.getValue().get("link");
        assertThat(link).contains("/ativar#");
        return link.substring(link.indexOf('#') + 1);
    }

    private List<EmailSaida> filaDe(String email) {
        return emailSaidaRepository.findAll().stream().filter(e -> email.equals(e.getDestinatario())).toList();
    }

    private Usuario conta(String email) {
        return usuarioRepository.findByEmailIgnoreCaseAndActiveTrue(email).orElseThrow();
    }

    @Test
    void coordenadorCadastraContaSemSenhaComPapelEEmailNaFila() throws Exception {
        String email = emailNovo();

        String resposta = cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), email, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.usuarioId").isNumber())
                .andExpect(jsonPath("$.usuarioPastoralId").isNumber())
                .andExpect(jsonPath("$.ativacaoEnviadaPara").value("n***@teste.dev"))
                .andReturn().getResponse().getContentAsString();

        assertThat(resposta).doesNotContain("token").doesNotContain("senha");
        Usuario criada = conta(email);
        assertThat(criada.getSenha()).isNull();
        assertThat(criada.getAtivadoEm()).isNull();
        assertThat(criada.estaAtivada()).isFalse();
        assertThat(usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(criada.getId(), cenario.pascom.getId()))
                .hasValueSatisfying(up -> assertThat(up.getPapel().name()).isEqualTo("MEMBRO"));
        assertThat(filaDe(email)).singleElement().satisfies(e -> {
            assertThat(e.getTemplate()).isEqualTo("ativacao");
            assertThat(e.getStatus()).isIn(StatusEmail.PENDENTE, StatusEmail.ENVIADO);
        });
        assertThat(tokenDoEmail(email)).isNotBlank();
    }

    @Test
    void emailRepetidoDa409ENadaFicaGravado() throws Exception {
        long antes = usuarioRepository.count();

        cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), cenario.membro1Pascom.getEmail(), null)
                .andExpect(status().isConflict());

        assertThat(usuarioRepository.count()).isEqualTo(antes);
        assertThat(filaDe(cenario.membro1Pascom.getEmail()).stream().filter(e -> "ativacao".equals(e.getTemplate())))
                .isEmpty();
    }

    @Test
    void falhaNoPapelDesfazAContaToda() throws Exception {
        String primeiro = emailNovo();
        cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), primeiro, "TECNICO").andExpect(status().isCreated());

        String segundo = emailNovo();
        cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), segundo, "TECNICO")
                .andExpect(status().isUnprocessableEntity());

        assertThat(usuarioRepository.findByEmailIgnoreCaseAndActiveTrue(segundo)).isEmpty();
        assertThat(filaDe(segundo)).isEmpty();
    }

    @Test
    void permissoesSaoAsDeHoje() throws Exception {
        // coordenador de outra pastoral: a pastoral nem aparece para ele
        cadastrar(cenario.coordenadorEcc, cenario.pascom.getId(), emailNovo(), null).andExpect(status().isNotFound());
        // membro comum
        cadastrar(cenario.membro1Pascom, cenario.pascom.getId(), emailNovo(), null).andExpect(status().isForbidden());
        // coordenador não atribui o papel de coordenador
        cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), emailNovo(), "COORDENADOR")
                .andExpect(status().isForbidden());
        // o padre pode
        cadastrar(cenario.padreA, cenario.pascom.getId(), emailNovo(), "COORDENADOR").andExpect(status().isCreated());
    }

    @Test
    void contaSemSenhaNaoEntraEDa401IgualAContaInexistente() throws Exception {
        String email = emailNovo();
        cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), email, null).andExpect(status().isCreated());

        String semSenha = entrar(email, "qualquer12345").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String vazia = entrar(email, "").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String inexistente = entrar(emailNovo(), "qualquer12345").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(semSenha).isEqualTo(inexistente);
        assertThat(vazia).isEqualTo(inexistente);
    }

    @Test
    void ativarDefineASenhaEDepoisOLoginEntra() throws Exception {
        String email = emailNovo();
        cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), email, null).andExpect(status().isCreated());
        String token = tokenDoEmail(email);

        publico("/api/contas/ativacao/detalhes", "{\"token\":\"" + token + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primeiroNome").value("Maria"))
                .andExpect(jsonPath("$.email").value("n***@teste.dev"));
        publico("/api/contas/ativar", "{\"token\":\"" + token + "\",\"senha\":\"curta\"}")
                .andExpect(status().isBadRequest());
        publico("/api/contas/ativar", "{\"token\":\"" + token + "\",\"senha\":\"senha-nova-123\"}")
                .andExpect(status().isNoContent());

        Usuario ativada = conta(email);
        assertThat(ativada.getAtivadoEm()).isNotNull();
        assertThat(passwordEncoder.matches("senha-nova-123", ativada.getSenha())).isTrue();
        entrar(email, "senha-nova-123").andExpect(status().isNoContent());

        // o mesmo link não serve duas vezes
        publico("/api/contas/ativar", "{\"token\":\"" + token + "\",\"senha\":\"outra-senha-123\"}")
                .andExpect(status().isNotFound());
        publico("/api/contas/ativacao/detalhes", "{\"token\":\"" + token + "\"}").andExpect(status().isNotFound());
    }

    @Test
    void tokenVencidoDa410EAContaContinuaSemSenha() throws Exception {
        String email = emailNovo();
        cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), email, null).andExpect(status().isCreated());
        String token = tokenDoEmail(email);
        TokenConta registro = tokenContaRepository.buscarPorHash(tokenConvite.hash(token)).orElseThrow();
        registro.setExpiraEm(LocalDateTime.now(clock).minusMinutes(1));
        tokenContaRepository.save(registro);

        publico("/api/contas/ativar", "{\"token\":\"" + token + "\",\"senha\":\"senha-nova-123\"}")
                .andExpect(status().isGone());

        assertThat(conta(email).getSenha()).isNull();
    }

    @Test
    void reenvioMataOLinkAntigoELimitaUmPorHora() throws Exception {
        String email = emailNovo();
        String resposta = cadastrar(cenario.coordenadorPascom, cenario.pascom.getId(), email, null)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long usuarioId = ((Number) JsonPath.read(resposta, "$.usuarioId")).longValue();
        String antigo = tokenDoEmail(email);

        // recém-cadastrado: o primeiro e-mail ainda conta como "enviado agora"
        reenviar(cenario.coordenadorPascom, usuarioId).andExpect(status().isTooManyRequests());

        TokenConta registro = tokenContaRepository.buscarPorHash(tokenConvite.hash(antigo)).orElseThrow();
        registro.setCriadoEm(LocalDateTime.now(clock).minusHours(2));
        tokenContaRepository.save(registro);
        reenviar(cenario.coordenadorPascom, usuarioId).andExpect(status().isNoContent());

        publico("/api/contas/ativacao/detalhes", "{\"token\":\"" + antigo + "\"}").andExpect(status().isNotFound());
        assertThat(filaDe(email).stream().filter(e -> "ativacao".equals(e.getTemplate()))).hasSize(2);
    }

    @Test
    void reenvioDeContaJaAtivadaDa422EDeOutraPastoralDa404() throws Exception {
        // membro1Pascom já tem senha: conta ativada
        reenviar(cenario.coordenadorPascom, cenario.membro1Pascom.getId()).andExpect(status().isUnprocessableEntity());
        reenviar(cenario.coordenadorEcc, cenario.membro1Pascom.getId()).andExpect(status().isNotFound());
    }

    private ResultActions reenviar(Usuario quem, Long usuarioId) throws Exception {
        return mvc.perform(post("/api/pastorais/{p}/membros/{u}/reenviar-ativacao", cenario.pascom.getId(), usuarioId)
                .with(user(cenario.principal(quem))).with(csrf()));
    }
}
