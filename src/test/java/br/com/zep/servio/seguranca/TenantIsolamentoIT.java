package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.dto.FuncaoRequestDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Recurso de outra paróquia não existe para quem está logado: 404, nunca 403 (não revela que existe). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class TenantIsolamentoIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PastoralRepository pastoralRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper objectMapper;

    private Pastoral criarPastoralDeOutraParoquia() {
        Paroquia paroquiaB = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia B");
        Pastoral pastoral = new Pastoral();
        pastoral.setNome("Pastoral de B");
        pastoral.setParoquiaId(paroquiaB.getId());
        return pastoralRepository.save(pastoral);
    }

    @Test
    void lerAlterarEExcluirDeOutraParoquiaDa404() throws Exception {
        Paroquia paroquiaA = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia A");
        ApoioTeste.criarUsuario(usuarioRepository, passwordEncoder, paroquiaA.getId(),
                "admin.a1@servio.dev", "senha12345", Perfil.ADMIN);
        Cookie sessao = ApoioTeste.login(mvc, "admin.a1@servio.dev", "senha12345");

        Pastoral pastoralDeB = criarPastoralDeOutraParoquia();

        mvc.perform(get("/api/pastorais/" + pastoralDeB.getId()).cookie(sessao))
                .andExpect(status().isNotFound());

        mvc.perform(put("/api/pastorais/" + pastoralDeB.getId()).cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Tentativa de alterar\"}"))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/pastorais/" + pastoralDeB.getId()).cookie(sessao).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void referenciarRecursoDeOutraParoquiaDa404() throws Exception {
        Paroquia paroquiaA = ApoioTeste.criarParoquia(paroquiaRepository, "Paróquia A2");
        ApoioTeste.criarUsuario(usuarioRepository, passwordEncoder, paroquiaA.getId(),
                "admin.a2@servio.dev", "senha12345", Perfil.ADMIN);
        Cookie sessao = ApoioTeste.login(mvc, "admin.a2@servio.dev", "senha12345");

        Pastoral pastoralDeB = criarPastoralDeOutraParoquia();
        String corpo = objectMapper.writeValueAsString(new FuncaoRequestDTO("Coroinha", pastoralDeB.getId()));

        mvc.perform(post("/api/funcoes").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isNotFound());
    }
}
