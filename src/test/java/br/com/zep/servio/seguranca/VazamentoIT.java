package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Nenhuma resposta traz senha/hash, erro não mostra stack trace, headers de segurança presentes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class VazamentoIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Cookie logarComoAdmin(String email) throws Exception {
        Paroquia paroquia = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia Vazamento");
        ApoioTeste.criarUsuario(usuarioRepository, passwordEncoder, paroquia.getId(), email, "senha12345", Perfil.ADMIN);
        return ApoioTeste.login(mvc, email, "senha12345");
    }

    @Test
    void respostaDeMeNaoTrazSenha() throws Exception {
        Cookie sessao = logarComoAdmin("vazamento1@servio.dev");

        mvc.perform(get("/api/me").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void listagemDeUsuariosNaoTrazSenha() throws Exception {
        Cookie sessao = logarComoAdmin("vazamento2@servio.dev");

        mvc.perform(get("/api/usuarios").cookie(sessao))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("senha"))))
                .andExpect(content().string(not(containsString("$2a$"))));
    }

    @Test
    void erroInternoNaoMostraStackTraceNemMensagemDetalhada() throws Exception {
        Cookie sessao = logarComoAdmin("vazamento3@servio.dev");

        mvc.perform(get("/api/usuarios/nao-e-um-numero").cookie(sessao))
                .andExpect(content().string(not(containsString("br.com.zep"))))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("\tat "))));
    }

    @Test
    void respostaTrazHeadersDeSeguranca() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }
}
