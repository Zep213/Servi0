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
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** fail-on-unknown-properties=true: campo que o DTO não declara (ex.: paroquiaId) vira 400, não é ignorado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class MassAssignmentIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void campoAMaisNoJsonDa400() throws Exception {
        Paroquia paroquia = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia Mass Assignment");
        ApoioTeste.criarUsuario(usuarioRepository, passwordEncoder, paroquia.getId(),
                "admin.mass@servio.dev", "senha12345", Perfil.ADMIN);
        Cookie sessao = ApoioTeste.login(mvc, "admin.mass@servio.dev", "senha12345");

        // paroquiaId não existe mais em PastoralRequestDTO: tentativa de mass assignment
        mvc.perform(post("/api/pastorais").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Pastoral X\",\"paroquiaId\":999}"))
                .andExpect(status().isBadRequest());
    }

    /** Telefone e aparência só mudam pelo próprio usuário (PUT /api/me): cadastro e edição de usuário não os aceitam. */
    @Test
    void cadastroEEdicaoDeUsuarioNaoAceitamTelefoneNemAparencia() throws Exception {
        Paroquia paroquia = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia Mass Assignment Telefone");
        var admin = ApoioTeste.criarUsuario(usuarioRepository, passwordEncoder, paroquia.getId(),
                "admin.tel@servio.dev", "senha12345", Perfil.ADMIN);
        Cookie sessao = ApoioTeste.login(mvc, "admin.tel@servio.dev", "senha12345");

        mvc.perform(post("/api/usuarios").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Novo\",\"email\":\"novo.tel@servio.dev\",\"senha\":\"senha12345\","
                                + "\"perfil\":\"SERVIDOR\",\"telefone\":\"+5583988124407\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/usuarios/{id}", admin.getId()).cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Admin\",\"email\":\"admin.tel@servio.dev\",\"perfil\":\"ADMIN\","
                                + "\"aparencia\":\"ESCURO\"}"))
                .andExpect(status().isBadRequest());
    }
}
