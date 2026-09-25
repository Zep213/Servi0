package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.dto.ReuniaoRequestDTO;
import br.com.zep.servio.model.dto.SolicitacaoReuniaoRequestDTO;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parte 5.8: reuniões — listagem por pastoral, e-mail a cada coordenador ao solicitar, e o caso sem coordenador nenhum. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class ReuniaoIT {

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

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
    }

    private String json(Object dto) {
        return objectMapper.writeValueAsString(dto);
    }

    @Test
    void reuniaoApareceSoNaPastoralDela() throws Exception {
        mvc.perform(post("/api/pastorais/{id}/reunioes", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReuniaoRequestDTO("Reunião da Pascom", LocalDateTime.now().plusDays(3), "Salão"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pastorais/{id}/reunioes", cenario.ecc.getId())
                        .with(user(cenario.principal(cenario.coordenadorEcc))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ReuniaoRequestDTO("Reunião do ECC", LocalDateTime.now().plusDays(3), "Salão"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/pastorais/{id}/reunioes", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("Reunião da Pascom");
                    assertThat(body).doesNotContain("Reunião do ECC");
                });
    }

    @Test
    void solicitacaoEnviaEmailACadaCoordenadorComAutorEMotivo() throws Exception {
        // Adiciona um 2º coordenador à Pascom (só padre/admin atribui COORDENADOR).
        Usuario segundoCoordenador = new Usuario();
        segundoCoordenador.setNome("Segundo Coordenador");
        segundoCoordenador.setEmail("segundo.coordenador." + System.nanoTime() + "@servio.dev");
        segundoCoordenador.setSenha(passwordEncoder.encode(CenarioParoquia.SENHA));
        segundoCoordenador.setPerfil(Perfil.SERVIDOR);
        segundoCoordenador.setParoquiaId(cenario.paroquiaA.getId());
        segundoCoordenador = usuarioRepository.save(segundoCoordenador);
        UsuarioPastoral participacao = new UsuarioPastoral();
        participacao.setUsuario(segundoCoordenador);
        participacao.setPastoral(cenario.pascom);
        participacao.setPapel(PapelPastoral.COORDENADOR);
        participacao.setParoquiaId(cenario.paroquiaA.getId());
        usuarioPastoralRepository.save(participacao);

        mvc.perform(post("/api/pastorais/{id}/reunioes/solicitar", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.vicePascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SolicitacaoReuniaoRequestDTO("Discutir a próxima campanha"))))
                .andExpect(status().isNoContent());

        verify(notificador, times(2)).notificar(org.mockito.ArgumentMatchers.anyString(),
                contains("Pedido de reunião"), contains("Discutir a próxima campanha"));
        verify(notificador).notificar(eq(cenario.coordenadorPascom.getEmail()),
                org.mockito.ArgumentMatchers.anyString(), contains(cenario.vicePascom.getNome()));
        verify(notificador).notificar(eq(segundoCoordenador.getEmail()),
                org.mockito.ArgumentMatchers.anyString(), contains(cenario.vicePascom.getNome()));
    }

    @Test
    void solicitarSemCoordenadorDa404ENenhumEmail() throws Exception {
        Pastoral semCoordenador = new Pastoral();
        semCoordenador.setNome("Pastoral sem coordenador " + System.nanoTime());
        semCoordenador.setParoquiaId(cenario.paroquiaA.getId());
        semCoordenador = pastoralRepository.save(semCoordenador);

        Usuario membro = new Usuario();
        membro.setNome("Membro solitário");
        membro.setEmail("membro.solitario." + System.nanoTime() + "@servio.dev");
        membro.setSenha(passwordEncoder.encode(CenarioParoquia.SENHA));
        membro.setPerfil(Perfil.SERVIDOR);
        membro.setParoquiaId(cenario.paroquiaA.getId());
        membro = usuarioRepository.save(membro);
        UsuarioPastoral participacao = new UsuarioPastoral();
        participacao.setUsuario(membro);
        participacao.setPastoral(semCoordenador);
        participacao.setPapel(PapelPastoral.MEMBRO);
        participacao.setParoquiaId(cenario.paroquiaA.getId());
        usuarioPastoralRepository.save(participacao);

        mvc.perform(post("/api/pastorais/{id}/reunioes/solicitar", semCoordenador.getId())
                        .with(user(cenario.principal(membro))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SolicitacaoReuniaoRequestDTO("Preciso falar com alguém"))))
                .andExpect(status().isNotFound());

        verify(notificador, never()).notificar(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
