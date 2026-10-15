package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.TentativasLogin;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class LoginIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    TentativasLogin tentativasLogin;

    @BeforeEach
    void limparTentativasDoIpPadrao() {
        // MockMvc, sem X-Forwarded-For, sempre reporta o mesmo IP de origem
        tentativasLogin.limpar(TentativasLogin.chaveIp(new org.springframework.mock.web.MockHttpServletRequest()));
    }

    private void criarUsuario(String email, String senha, Perfil perfil) {
        Paroquia paroquia = new Paroquia();
        paroquia.setNome("Paróquia de Teste");
        paroquia.setEmailContato(email);
        paroquiaRepository.save(paroquia);

        Usuario usuario = new Usuario();
        usuario.setNome("Usuário de Teste");
        usuario.setEmail(email);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setPerfil(perfil);
        usuario.setParoquiaId(paroquia.getId());
        usuarioRepository.save(usuario);
    }

    @Test
    void loginValidoRetorna204ComCookieSeguro() throws Exception {
        criarUsuario("padre@servio.dev", "senha12345", Perfil.PADRE);

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "padre@servio.dev")
                        .param("senha", "senha12345"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("SERVIO_SESSION"))
                .andExpect(cookie().httpOnly("SERVIO_SESSION", true));
    }

    @Test
    void emailInexistenteESenhaErradaRespondemIgual() throws Exception {
        criarUsuario("existe@servio.dev", "senha12345", Perfil.SERVIDOR);

        MvcResult semConta = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "naoexiste@servio.dev")
                        .param("senha", "qualquer1234"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult senhaErrada = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "existe@servio.dev")
                        .param("senha", "senhaErrada1"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(senhaErrada.getResponse().getStatus()).isEqualTo(semConta.getResponse().getStatus());
        assertThat(senhaErrada.getResponse().getContentAsString()).isEqualTo(semConta.getResponse().getContentAsString());
    }

    @Test
    void sextaTentativaErradaRetorna429() throws Exception {
        criarUsuario("bloqueio@servio.dev", "senha12345", Perfil.SERVIDOR);

        for (int i = 0; i < TentativasLogin.MAX_FALHAS; i++) {
            mvc.perform(post("/api/auth/login").with(csrf())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("login", "bloqueio@servio.dev")
                            .param("senha", "senhaErrada1"))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "bloqueio@servio.dev")
                        .param("senha", "senha12345"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    @Test
    void idDaSessaoMudaAposLogin() throws Exception {
        criarUsuario("sessao@servio.dev", "senha12345", Perfil.SERVIDOR);

        // primeiro login "fixa" uma sessão válida, como se fosse a do atacante
        MvcResult primeiro = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "sessao@servio.dev")
                        .param("senha", "senha12345"))
                .andExpect(status().isNoContent())
                .andReturn();
        String idAntes = primeiro.getResponse().getCookie("SERVIO_SESSION").getValue();

        // login de novo, apresentando essa mesma sessão: o id tem que trocar
        MvcResult segundo = mvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("SERVIO_SESSION", idAntes)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "sessao@servio.dev")
                        .param("senha", "senha12345"))
                .andExpect(status().isNoContent())
                .andReturn();
        String idDepois = segundo.getResponse().getCookie("SERVIO_SESSION").getValue();

        assertThat(idDepois).isNotEqualTo(idAntes);
    }

    @Test
    void cookieAntigoNaoValeDepoisDoLogout() throws Exception {
        criarUsuario("logout@servio.dev", "senha12345", Perfil.SERVIDOR);

        MvcResult login = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", "logout@servio.dev")
                        .param("senha", "senha12345"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessao = login.getResponse().getCookie("SERVIO_SESSION");

        mvc.perform(post("/api/auth/logout").cookie(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/me").cookie(sessao))
                .andExpect(status().isUnauthorized());
    }

    private Usuario usuarioComTelefone(String email, String telefone) {
        criarUsuario(email, "senha12345", Perfil.SERVIDOR);
        Usuario usuario = usuarioRepository.findByEmailIgnoreCaseAndActiveTrue(email).orElseThrow();
        usuario.setTelefone(telefone);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private org.springframework.test.web.servlet.ResultActions entrar(String login, String senha) throws Exception {
        return entrar(login, senha, "127.0.0.1");
    }

    private org.springframework.test.web.servlet.ResultActions entrar(String login, String senha, String ip)
            throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                .with(req -> {
                    req.setRemoteAddr(ip);
                    return req;
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("login", login)
                .param("senha", senha));
    }

    @Test
    void entraComTelefoneEmTresFormatosNaMesmaConta() throws Exception {
        Usuario usuario = usuarioComTelefone("fone@servio.dev", "+5583988124407");

        for (String formato : new String[] {"(83) 9 8812-4407", "83988124407", "+55 83 98812-4407"}) {
            MvcResult login = entrar(formato, "senha12345").andExpect(status().isNoContent()).andReturn();
            mvc.perform(get("/api/me").cookie(login.getResponse().getCookie("SERVIO_SESSION")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(usuario.getId()));
        }
    }

    @Test
    void telefoneInexistenteDa401ComAMesmaMensagem() throws Exception {
        usuarioComTelefone("fone2@servio.dev", "+5583988124407");

        String semConta = entrar("(11) 98888-7777", "senha12345")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String senhaErrada = entrar("(83) 98812-4407", "senhaErrada1")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(semConta).isEqualTo(senhaErrada).contains("E-mail, telefone ou senha inválidos");
    }

    @Test
    void falhasPeloEmailEPeloTelefoneDaMesmaPessoaSomam() throws Exception {
        usuarioComTelefone("soma@servio.dev", "+5583988124407");

        // um IP diferente por tentativa: o bloqueio final só pode vir da chave da conta, não do IP
        for (int i = 0; i < 3; i++) {
            entrar("soma@servio.dev", "senhaErrada1", "10.0.0." + i).andExpect(status().isUnauthorized());
        }
        for (int i = 0; i < 2; i++) {
            entrar("83988124407", "senhaErrada1", "10.0.1." + i).andExpect(status().isUnauthorized());
        }

        entrar("soma@servio.dev", "senha12345", "10.0.2.1").andExpect(status().isTooManyRequests());
        entrar("(83) 98812-4407", "senha12345", "10.0.2.2").andExpect(status().isTooManyRequests());
    }
}
