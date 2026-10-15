package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.EmailSaida;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.Perfil;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Esqueci a senha" (Sprint 2, Cartão 3). Relógio controlado (limite por hora e prazo de 1 hora), sem
 * {@code @Transactional}: o pedido roda fora da requisição e o e-mail só entra na fila no commit. O link chega
 * pelas variáveis entregues ao {@code Notificador} mockado. Cada teste usa e-mail e IP próprios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class RecuperacaoIT {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    private static final AtomicInteger IPS = new AtomicInteger();

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
        return "10.8." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions publico(String rota, String corpo) throws Exception {
        String ip = ipUnico();
        return mvc.perform(post(rota).contentType(MediaType.APPLICATION_JSON).content(corpo).with(csrf())
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                }));
    }

    private ResultActions pedir(String login) throws Exception {
        return publico("/api/contas/recuperacao", "{\"login\":\"" + login + "\"}");
    }

    private ResultActions redefinir(String token, String senha) throws Exception {
        return publico("/api/contas/redefinir-senha", "{\"token\":\"" + token + "\",\"senha\":\"" + senha + "\"}");
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

    private List<EmailSaida> recuperacoesDe(String email) {
        return emailSaidaRepository.findAll().stream()
                .filter(e -> email.equals(e.getDestinatario()) && "recuperacao".equals(e.getTemplate())).toList();
    }

    /** Todos os tokens que o Notificador recebeu para este e-mail, do mais antigo ao mais novo. */
    private List<String> tokensEnviados(String email, int esperados) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> vars =
                (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
        verify(notificador, timeout(5000).atLeast(esperados))
                .enviar(eq(email), anyString(), eq("recuperacao"), vars.capture());
        return vars.getAllValues().stream().map(v -> {
            String link = (String) v.get("link");
            assertThat(link).contains("/redefinir-senha#");
            return link.substring(link.indexOf('#') + 1);
        }).toList();
    }

    /** Para provar que NADA foi enviado: espera um pouco e confere que a fila continua vazia. */
    private void semEmailPara(String email) {
        Awaitility.await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(3))
                .until(() -> recuperacoesDe(email).isEmpty());
    }

    @Test
    void mesmaRespostaExistaAContaOuNao() throws Exception {
        String inexistente = "nao.existe." + UUID.randomUUID() + "@teste.dev";

        String existente = pedir(pessoa.getEmail()).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String naoExiste = pedir(inexistente).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(existente).isEqualTo(naoExiste).isEmpty();
        assertThat(tokensEnviados(pessoa.getEmail(), 1)).hasSize(1);
        semEmailPara(inexistente);
        pedir(" ").andExpect(status().isBadRequest());
    }

    @Test
    void pedidoPorTelefoneEmQualquerFormatoVaiParaOEmailDaConta() throws Exception {
        String digitos = String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        Usuario conta = usuarioRepository.findById(pessoa.getId()).orElseThrow();
        conta.setTelefone("+55839" + digitos);
        usuarioRepository.save(conta);

        pedir("(83) 9 " + digitos.substring(0, 4) + "-" + digitos.substring(4)).andExpect(status().isAccepted());
        pedir("83 9" + digitos).andExpect(status().isAccepted());

        assertThat(tokensEnviados(pessoa.getEmail(), 2)).hasSize(2);
    }

    @Test
    void quartoPedidoNaHoraNaoEnviaEDepoisDeUmaHoraVoltaAEnviar() throws Exception {
        for (int i = 0; i < 4; i++) {
            pedir(pessoa.getEmail()).andExpect(status().isAccepted());
        }
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> recuperacoesDe(pessoa.getEmail()).size() == 3);
        Awaitility.await().during(Duration.ofMillis(700)).atMost(Duration.ofSeconds(3))
                .until(() -> recuperacoesDe(pessoa.getEmail()).size() == 3);

        RELOGIO.instante = RELOGIO.instante.plus(61, ChronoUnit.MINUTES);
        pedir(pessoa.getEmail()).andExpect(status().isAccepted());

        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> recuperacoesDe(pessoa.getEmail()).size() == 4);
    }

    @Test
    void redefinirTrocaASenhaEDerrubaAsSessoes(CapturedOutput saida) throws Exception {
        Cookie sessao = entrar(pessoa.getEmail(), CenarioParoquia.SENHA).andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SERVIO_SESSION");
        mvc.perform(get("/api/me").cookie(sessao)).andExpect(status().isOk());

        pedir(pessoa.getEmail()).andExpect(status().isAccepted());
        String token = tokensEnviados(pessoa.getEmail(), 1).get(0);
        publico("/api/contas/recuperacao/detalhes", "{\"token\":\"" + token + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.primeiroNome").isNotEmpty());
        redefinir(token, "curta").andExpect(status().isBadRequest());
        redefinir(token, "senha-nova-456").andExpect(status().isNoContent());

        entrar(pessoa.getEmail(), CenarioParoquia.SENHA).andExpect(status().isUnauthorized());
        entrar(pessoa.getEmail(), "senha-nova-456").andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(sessao)).andExpect(status().isUnauthorized());
        assertThat(saida.getAll()).doesNotContain(token).doesNotContain("senha-nova-456");
    }

    @Test
    void tokenUsadoVencidoESubstituidoPorNovoPedido() throws Exception {
        pedir(pessoa.getEmail()).andExpect(status().isAccepted());
        String primeiro = tokensEnviados(pessoa.getEmail(), 1).get(0);
        pedir(pessoa.getEmail()).andExpect(status().isAccepted());
        List<String> todos = tokensEnviados(pessoa.getEmail(), 2);
        String segundo = todos.get(todos.size() - 1);
        assertThat(segundo).isNotEqualTo(primeiro);

        // o segundo pedido matou o link do primeiro
        publico("/api/contas/recuperacao/detalhes", "{\"token\":\"" + primeiro + "\"}").andExpect(status().isNotFound());
        redefinir(segundo, "senha-nova-456").andExpect(status().isNoContent());
        redefinir(segundo, "outra-senha-789").andExpect(status().isNotFound());

        // vencido: mais de 1 hora
        pedir(pessoa.getEmail()).andExpect(status().isAccepted());
        List<String> depois = tokensEnviados(pessoa.getEmail(), 3);
        String vencivel = depois.get(depois.size() - 1);
        RELOGIO.instante = RELOGIO.instante.plus(61, ChronoUnit.MINUTES);
        redefinir(vencivel, "senha-nova-456").andExpect(status().isGone());
    }

    @Test
    void redefinirLimpaOBloqueioDeLoginDaConta() throws Exception {
        for (int i = 0; i < 5; i++) {
            entrar(pessoa.getEmail(), "senha-errada-" + i).andExpect(status().isUnauthorized());
        }
        entrar(pessoa.getEmail(), CenarioParoquia.SENHA).andExpect(status().isTooManyRequests());

        pedir(pessoa.getEmail()).andExpect(status().isAccepted());
        String token = tokensEnviados(pessoa.getEmail(), 1).get(0);
        redefinir(token, "senha-nova-456").andExpect(status().isNoContent());

        entrar(pessoa.getEmail(), "senha-nova-456").andExpect(status().isNoContent());
    }

    @Test
    void contaNaoAtivadaEContaDesativadaDao202SemEmail() throws Exception {
        Usuario pendente = new Usuario();
        pendente.setNome("Pendente Teste");
        pendente.setEmail("pendente." + UUID.randomUUID() + "@teste.dev");
        pendente.setPerfil(Perfil.SERVIDOR);
        pendente.setParoquiaId(pessoa.getParoquiaId());
        usuarioRepository.save(pendente);

        Usuario desativada = usuarioRepository.findById(cenario.membro2Pascom.getId()).orElseThrow();
        desativada.setActive(false);
        usuarioRepository.save(desativada);

        pedir(pendente.getEmail()).andExpect(status().isAccepted());
        pedir(desativada.getEmail()).andExpect(status().isAccepted());

        semEmailPara(pendente.getEmail());
        semEmailPara(desativada.getEmail());
    }
}
