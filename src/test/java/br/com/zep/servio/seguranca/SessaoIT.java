package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.UsuarioUpdateDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                new UsuarioUpdateDTO(alvo.getNome(), alvo.getEmail(), null, Perfil.COORDENADOR));

        mvc.perform(put("/api/usuarios/" + alvo.getId()).cookie(sessaoDoAdmin).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());

        mvc.perform(get("/api/me").cookie(sessaoDoAlvo)).andExpect(status().isUnauthorized());
    }
}
