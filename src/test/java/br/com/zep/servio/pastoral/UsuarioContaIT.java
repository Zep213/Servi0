package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.dto.UsuarioRequestDTO;
import br.com.zep.servio.model.dto.UsuarioUpdateDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.notification.Notificador;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parte 5.8: quem pode criar/editar qual tipo de conta — coordenador só SERVIDOR, padre nunca ADMIN. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class UsuarioContaIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
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
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    int contador;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
    }

    private String json(Object dto) {
        return objectMapper.writeValueAsString(dto);
    }

    private String emailFresco() {
        contador++;
        return "nova.conta" + contador + "." + System.nanoTime() + "@servio.dev";
    }

    @Test
    void coordenadorCriaServidorMasNaoPadreOuAdmin() throws Exception {
        mvc.perform(post("/api/usuarios").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioRequestDTO("Novo Servidor", emailFresco(), "senha12345", Perfil.SERVIDOR))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/usuarios").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioRequestDTO("Novo Padre", emailFresco(), "senha12345", Perfil.PADRE))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/usuarios").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioRequestDTO("Novo Admin", emailFresco(), "senha12345", Perfil.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void padreNaoCriaAdmin() throws Exception {
        mvc.perform(post("/api/usuarios").with(user(cenario.principal(cenario.padreA))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioRequestDTO("Novo Admin", emailFresco(), "senha12345", Perfil.ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void coordenadorNaoEditaContaDoPadre() throws Exception {
        String corpo = json(new UsuarioUpdateDTO(cenario.padreA.getNome(), cenario.padreA.getEmail(), null, Perfil.PADRE));
        mvc.perform(put("/api/usuarios/{id}", cenario.padreA.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }
}
