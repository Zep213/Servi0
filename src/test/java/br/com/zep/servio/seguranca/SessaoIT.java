package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.UsuarioUpdateDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.pastoral.CenarioParoquia;
import br.com.zep.servio.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O CrudService.atualizar de Usuario derruba a sessão do alvo quando perfil/e-mail/senha mudam. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class SessaoIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PastoralRepository pastoralRepository;
    @Autowired
    FuncaoRepository funcaoRepository;
    @Autowired
    UsuarioPastoralRepository usuarioPastoralRepository;

    private static final long DIAS = 24L * 60 * 60;

    private int intervaloDaSessao(Cookie sessao) {
        // o Spring Session grava o id da sessão em Base64 no cookie; no banco ele fica como UUID puro
        String sessionId = new String(java.util.Base64.getDecoder().decode(sessao.getValue()),
                java.nio.charset.StandardCharsets.UTF_8);
        return jdbc.queryForObject("select max_inactive_interval from spring_session where session_id = ?",
                Integer.class, sessionId);
    }

    private MvcResult entrar(String email, String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("login", email).param("senha", senha))
                .andExpect(status().isNoContent()).andReturn();
    }

    @Test
    void membroFicaSessentaDiasECookieTemMaxAge() throws Exception {
        CenarioParoquia cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        MvcResult login = entrar(cenario.membro1Pascom.getEmail(), CenarioParoquia.SENHA);
        Cookie sessao = login.getResponse().getCookie("SERVIO_SESSION");

        assertThat(intervaloDaSessao(sessao)).isEqualTo(60 * DIAS);
        assertThat(sessao.getMaxAge()).isEqualTo((int) (60 * DIAS));
    }

    @Test
    void coordenadorEPadreFicamSeteDias() throws Exception {
        CenarioParoquia cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();

        Cookie coordenador = entrar(cenario.coordenadorPascom.getEmail(), CenarioParoquia.SENHA)
                .getResponse().getCookie("SERVIO_SESSION");
        Cookie padre = entrar(cenario.padreA.getEmail(), CenarioParoquia.SENHA)
                .getResponse().getCookie("SERVIO_SESSION");

        assertThat(intervaloDaSessao(coordenador)).isEqualTo(7 * DIAS);
        assertThat(intervaloDaSessao(padre)).isEqualTo(7 * DIAS);
    }

    @Test
    void rebaixarPerfilDerrubaASessao() throws Exception {
        Paroquia paroquia = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia da Sessão");
        ApoioTeste.criarUsuario(usuarioRepository, passwordEncoder, paroquia.getId(),
                "admin.sessao@servio.dev", "senha12345", Perfil.ADMIN);
        Usuario alvo = ApoioTeste.criarUsuario(usuarioRepository, passwordEncoder, paroquia.getId(),
                "alvo.sessao@servio.dev", "senha12345", Perfil.SERVIDOR);

        Cookie sessaoDoAlvo = ApoioTeste.login(mvc, "alvo.sessao@servio.dev", "senha12345");
        mvc.perform(get("/api/me").cookie(sessaoDoAlvo)).andExpect(status().isOk());

        Cookie sessaoDoAdmin = ApoioTeste.login(mvc, "admin.sessao@servio.dev", "senha12345");
        String corpo = objectMapper.writeValueAsString(
                new UsuarioUpdateDTO(alvo.getNome(), alvo.getEmail(), null, Perfil.PADRE));

        mvc.perform(put("/api/usuarios/" + alvo.getId()).cookie(sessaoDoAdmin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());

        mvc.perform(get("/api/me").cookie(sessaoDoAlvo)).andExpect(status().isUnauthorized());
    }
}
