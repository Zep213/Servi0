package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.EmailSaida;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.EmailSaidaRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.notification.Notificador;
import jakarta.servlet.http.Cookie;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Aviso de senha alterada e "Proteger minha conta" (Sprint 2, Cartão 4). Relógio controlado (prazo de 7 dias do
 * link de proteção), sem {@code @Transactional}: o e-mail só entra na fila no commit. Os links chegam pelas
 * variáveis entregues ao {@code Notificador} mockado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class SenhaAlteradaIT {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    private static final AtomicInteger IPS = new AtomicInteger();
    private static final String IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 "
            + "(KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1";
    private static final String ANDROID = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

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
    }

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
    EmailSaidaRepository emailSaidaRepository;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    Usuario pessoa;

    @BeforeEach
    void setUp() {
        RELOGIO.instante = Instant.now();
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        pessoa = cenario.membro1Pascom;
    }

    private static String ipUnico() {
        int n = IPS.getAndIncrement();
        return "10.9." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions trocarSenha(Usuario quem, String userAgent, String atual, String nova) throws Exception {
        return mvc.perform(post("/api/me/senha").with(user(cenario.principal(quem))).with(csrf())
                .header("User-Agent", userAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senhaAtual\":\"" + atual + "\",\"senhaNova\":\"" + nova + "\"}"));
    }

    private ResultActions publico(String rota, String corpo, String userAgent) throws Exception {
        String ip = ipUnico();
        return mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(corpo).with(csrf())
                .header("User-Agent", userAgent)
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

    /** Variáveis de todos os e-mails deste template que o Notificador recebeu para o endereço, em ordem. */
    private List<Map<String, Object>> emails(String email, String template, int esperados) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> vars =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(notificador, timeout(5000).atLeast(esperados)).enviar(eq(email), anyString(), eq(template), vars.capture());
        return vars.getAllValues();
    }

    private static String tokenDoLink(Map<String, Object> variaveis, String fragmento) {
        String link = (String) variaveis.get("link");
        assertThat(link).contains(fragmento + "#");
        return link.substring(link.indexOf('#') + 1);
    }

    private List<EmailSaida> filaDe(String email, String template) {
        return emailSaidaRepository.findAll().stream()
                .filter(e -> email.equals(e.getDestinatario()) && template.equals(e.getTemplate())).toList();
    }

    private void semEmail(String email, String template) {
        Awaitility.await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(3))
                .until(() -> filaDe(email, template).isEmpty());
    }

    @Test
    void trocarSenhaAvisaComOTipoDeAparelhoESemDadosAMais(CapturedOutput saida) throws Exception {
        trocarSenha(pessoa, IPHONE, CenarioParoquia.SENHA, "senha-nova-456").andExpect(status().isNoContent());

        Map<String, Object> aviso = emails(pessoa.getEmail(), "senha-alterada", 1).get(0);
        assertThat(aviso.keySet()).containsExactlyInAnyOrder("assunto", "nome", "quando", "aparelho", "coordenacao", "link");
        assertThat(aviso.get("aparelho")).isEqualTo("Celular iPhone");
        assertThat(aviso.get("coordenacao")).isEqualTo(false);
        assertThat(aviso.values().stream().map(String::valueOf)).noneMatch(v -> v.contains("senha-nova-456"));
        assertThat((String) aviso.get("link")).contains("/proteger-conta#");
        assertThat(saida.getAll()).doesNotContain(tokenDoLink(aviso, "/proteger-conta")).doesNotContain("senha-nova-456");
    }

    @Test
    void redefinirPorLinkTambemAvisa() throws Exception {
        publico("/api/contas/recuperacao", "{\"login\":\"" + pessoa.getEmail() + "\"}", ANDROID)
                .andExpect(status().isAccepted());
        String token = tokenDoLink(emails(pessoa.getEmail(), "recuperacao", 1).get(0), "/redefinir-senha");

        publico("/api/contas/redefinir-senha", "{\"token\":\"" + token + "\",\"senha\":\"senha-nova-456\"}", ANDROID)
                .andExpect(status().isNoContent());

        Map<String, Object> aviso = emails(pessoa.getEmail(), "senha-alterada", 1).get(0);
        assertThat(aviso.get("aparelho")).isEqualTo("Celular Android");
        assertThat((String) aviso.get("link")).contains("/proteger-conta#");
    }

    @Test
    void trocaPelaCoordenacaoAvisaSemAparelho() throws Exception {
        mvc.perform(put("/api/usuarios/{id}", pessoa.getId()).with(user(cenario.principal(cenario.padreA))).with(csrf())
                        .header("User-Agent", IPHONE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"" + pessoa.getNome() + "\",\"email\":\"" + pessoa.getEmail()
                                + "\",\"senha\":\"senha-do-padre-1\",\"perfil\":\"SERVIDOR\"}"))
                .andExpect(status().isOk());

        Map<String, Object> aviso = emails(pessoa.getEmail(), "senha-alterada", 1).get(0);
        assertThat(aviso.get("coordenacao")).isEqualTo(true);
        assertThat(aviso.get("aparelho")).isNull();
    }

    @Test
    void ativacaoNaoAvisa() throws Exception {
        String email = "novo." + UUID.randomUUID() + "@teste.dev";
        mvc.perform(post("/api/pastorais/{id}/membros", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Maria da Silva\",\"email\":\"" + email + "\"}"))
                .andExpect(status().isCreated());
        String token = tokenDoLink(emails(email, "ativacao", 1).get(0), "/ativar");

        publico("/api/contas/ativar", "{\"token\":\"" + token + "\",\"senha\":\"senha-nova-456\"}", ANDROID)
                .andExpect(status().isNoContent());

        semEmail(email, "senha-alterada");
    }

    @Test
    void protegerZeraASenhaDerrubaSessoesEMandaRecuperacao() throws Exception {
        trocarSenha(pessoa, IPHONE, CenarioParoquia.SENHA, "senha-do-invasor-9").andExpect(status().isNoContent());
        String tokenProtecao = tokenDoLink(emails(pessoa.getEmail(), "senha-alterada", 1).get(0), "/proteger-conta");
        Cookie sessao = entrar(pessoa.getEmail(), "senha-do-invasor-9").andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SERVIO_SESSION");
        mvc.perform(get("/api/me").cookie(sessao)).andExpect(status().isOk());

        publico("/api/contas/protecao/detalhes", "{\"token\":\"" + tokenProtecao + "\"}", ANDROID)
                .andExpect(status().isOk());
        publico("/api/contas/protecao", "{\"token\":\"" + tokenProtecao + "\"}", ANDROID)
                .andExpect(status().isNoContent());

        Usuario protegida = usuarioRepository.findById(pessoa.getId()).orElseThrow();
        assertThat(protegida.getSenha()).isNull();
        assertThat(protegida.getAtivadoEm()).isNotNull();
        mvc.perform(get("/api/me").cookie(sessao)).andExpect(status().isUnauthorized());
        entrar(pessoa.getEmail(), "senha-do-invasor-9").andExpect(status().isUnauthorized());
        entrar(pessoa.getEmail(), CenarioParoquia.SENHA).andExpect(status().isUnauthorized());
        // o mesmo link não serve duas vezes
        publico("/api/contas/protecao", "{\"token\":\"" + tokenProtecao + "\"}", ANDROID)
                .andExpect(status().isNotFound());

        // a recuperação foi para o e-mail da conta e devolve o acesso
        String tokenRecuperacao = tokenDoLink(emails(pessoa.getEmail(), "recuperacao", 1).get(0), "/redefinir-senha");
        publico("/api/contas/redefinir-senha", "{\"token\":\"" + tokenRecuperacao + "\",\"senha\":\"senha-certa-321\"}",
                ANDROID).andExpect(status().isNoContent());
        entrar(pessoa.getEmail(), "senha-certa-321").andExpect(status().isNoContent());
    }

    @Test
    void tokenDeProtecaoVencidoDa410EDeOutroTipoDa404() throws Exception {
        trocarSenha(pessoa, IPHONE, CenarioParoquia.SENHA, "senha-nova-456").andExpect(status().isNoContent());
        String protecao = tokenDoLink(emails(pessoa.getEmail(), "senha-alterada", 1).get(0), "/proteger-conta");
        publico("/api/contas/recuperacao", "{\"login\":\"" + pessoa.getEmail() + "\"}", ANDROID)
                .andExpect(status().isAccepted());
        String recuperacao = tokenDoLink(emails(pessoa.getEmail(), "recuperacao", 1).get(0), "/redefinir-senha");

        // token de outro tipo na rota de proteção: 404, e ele não foi gasto
        publico("/api/contas/protecao", "{\"token\":\"" + recuperacao + "\"}", ANDROID).andExpect(status().isNotFound());
        publico("/api/contas/recuperacao/detalhes", "{\"token\":\"" + recuperacao + "\"}", ANDROID)
                .andExpect(status().isOk());

        RELOGIO.instante = RELOGIO.instante.plus(7 * 24 * 60 + 1, ChronoUnit.MINUTES);
        publico("/api/contas/protecao", "{\"token\":\"" + protecao + "\"}", ANDROID).andExpect(status().isGone());
        assertThat(usuarioRepository.findById(pessoa.getId()).orElseThrow().getSenha()).isNotNull();
    }
}
