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
                        .param("email", "padre@servio.dev")
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
                        .param("email", "naoexiste@servio.dev")
                        .param("senha", "qualquer1234"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        MvcResult senhaErrada = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "existe@servio.dev")
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
                            .param("email", "bloqueio@servio.dev")
                            .param("senha", "senhaErrada1"))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "bloqueio@servio.dev")
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
                        .param("email", "sessao@servio.dev")
                        .param("senha", "senha12345"))
                .andExpect(status().isNoContent())
                .andReturn();
        String idAntes = primeiro.getResponse().getCookie("SERVIO_SESSION").getValue();

        // login de novo, apresentando essa mesma sessão: o id tem que trocar
        MvcResult segundo = mvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("SERVIO_SESSION", idAntes)).with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", "sessao@servio.dev")
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
                        .param("email", "logout@servio.dev")
                        .param("senha", "senha12345"))
                .andExpect(status().isNoContent())
                .andReturn();
        Cookie sessao = login.getResponse().getCookie("SERVIO_SESSION");

        mvc.perform(post("/api/auth/logout").cookie(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/me").cookie(sessao))
                .andExpect(status().isUnauthorized());
    }
}
