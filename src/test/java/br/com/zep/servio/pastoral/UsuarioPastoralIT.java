package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.UsuarioPastoralRequestDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parte 5.8: limites e regras de {@code UsuarioPastoral} — máximo de secretários, duplicidade e independência entre pastorais. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class UsuarioPastoralIT {

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
    int contadorCandidatos;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
    }

    private String json(Object dto) {
        return objectMapper.writeValueAsString(dto);
    }

    private Usuario candidatoFresco() {
        contadorCandidatos++;
        Usuario usuario = new Usuario();
        usuario.setNome("Candidato " + contadorCandidatos);
        usuario.setEmail("candidato" + contadorCandidatos + "." + System.nanoTime() + "@servio.dev");
        usuario.setSenha(passwordEncoder.encode(CenarioParoquia.SENHA));
        usuario.setPerfil(Perfil.SERVIDOR);
        usuario.setParoquiaId(cenario.paroquiaA.getId());
        return usuarioRepository.save(usuario);
    }

    @Test
    void terceiroSecretarioDaErro() throws Exception {
        // A Pascom do cenário já nasce com 2 secretários (secretario1Pascom, secretario2Pascom).
        Usuario candidato = candidatoFresco();
        String corpo = json(new UsuarioPastoralRequestDTO(candidato.getId(), cenario.pascom.getId(), PapelPastoral.SECRETARIO));
        mvc.perform(post("/api/usuarios-pastorais").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void mesmaPessoaDuasVezesNaPastoralDaConflito() throws Exception {
        String corpo = json(new UsuarioPastoralRequestDTO(cenario.membro1Pascom.getId(), cenario.pascom.getId(), PapelPastoral.TESOUREIRO));
        mvc.perform(post("/api/usuarios-pastorais").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isConflict());
    }

    @Test
    void papeisDiferentesEmPastoraisDiferentesDaCreated() throws Exception {
        // coordenadorPascom já é COORDENADOR da Pascom; entrar como MEMBRO do ECC é uma pastoral diferente, sem conflito.
        String corpo = json(new UsuarioPastoralRequestDTO(cenario.coordenadorPascom.getId(), cenario.ecc.getId(), PapelPastoral.MEMBRO));
        mvc.perform(post("/api/usuarios-pastorais").with(user(cenario.principal(cenario.coordenadorEcc))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
    }
}
