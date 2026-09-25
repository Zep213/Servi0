package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.CelebracaoRequestDTO;
import br.com.zep.servio.model.AuditLog;
import br.com.zep.servio.repository.AuditLogRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.ApoioTeste;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.notification.Notificador;
import jakarta.servlet.http.Cookie;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parte 5.3: o padre da Paróquia B contra recurso da Paróquia A (zero relação, tenant diferente)
 * dá sempre 404; e o ADMIN só enxerga dados de uma paróquia enquanto a tiver assumida (Parte 2.2),
 * com cada assumir/sair/escrita gerando o registro de auditoria esperado (já implementado na
 * Parte 2, testado aqui pela primeira vez fim-a-fim).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class EntreParoquiasIT {

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
    ComunidadeRepository comunidadeRepository;
    @Autowired
    CelebracaoRepository celebracaoRepository;
    @Autowired
    VagaRepository vagaRepository;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    Comunidade comunidade;
    Celebracao celebracaoA;
    Vaga vagaA;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        comunidade = new Comunidade();
        comunidade.setNome("Comunidade Central");
        comunidade.setParoquiaId(cenario.paroquiaA.getId());
        comunidade = comunidadeRepository.save(comunidade);

        celebracaoA = new Celebracao();
        celebracaoA.setComunidade(comunidade);
        celebracaoA.setData(LocalDate.now().plusDays(25));
        celebracaoA.setHora(LocalTime.of(10, 0));
        celebracaoA.setParoquiaId(cenario.paroquiaA.getId());
        celebracaoA = celebracaoRepository.save(celebracaoA);

        vagaA = new Vaga();
        vagaA.setCelebracao(celebracaoA);
        vagaA.setFuncao(cenario.funcaoComunicacaoPascom);
        vagaA.setQuantidade(5);
        vagaA.setParoquiaId(cenario.paroquiaA.getId());
        vagaA = vagaRepository.save(vagaA);
    }

    private String json(Object dto) {
        return objectMapper.writeValueAsString(dto);
    }

    @Test
    void padreDeOutraParoquiaNaoAcessaCelebracaoDaA() throws Exception {
        mvc.perform(get("/api/celebracoes/{id}", celebracaoA.getId())
                        .with(user(cenario.principal(cenario.padreB))).with(csrf()))
                .andExpect(status().isNotFound());

        String corpo = json(new CelebracaoRequestDTO(comunidade.getId(), LocalDate.now().plusDays(26), LocalTime.of(11, 0), null, null, null));
        mvc.perform(put("/api/celebracoes/{id}", celebracaoA.getId())
                        .with(user(cenario.principal(cenario.padreB))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/celebracoes/{id}", celebracaoA.getId())
                        .with(user(cenario.principal(cenario.padreB))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void padreDeOutraParoquiaNaoAcessaVagaDaA() throws Exception {
        mvc.perform(get("/api/vagas/{id}", vagaA.getId())
                        .with(user(cenario.principal(cenario.padreB))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void padreDeOutraParoquiaNaoAcessaPastoralDaA() throws Exception {
        mvc.perform(get("/api/pastorais/{id}", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.padreB))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminSemAssumirNaoVeDadosDaParoquiaA() throws Exception {
        Cookie sessao = ApoioTeste.login(mvc, cenario.admin.getEmail(), CenarioParoquia.SENHA);

        mvc.perform(get("/api/celebracoes").cookie(sessao).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("\"id\":" + celebracaoA.getId()));

        mvc.perform(get("/api/pastorais").cookie(sessao).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(cenario.pascom.getNome()));

        mvc.perform(get("/api/celebracoes/{id}", celebracaoA.getId()).cookie(sessao).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminDepoisDeAssumirVeDadosDaParoquiaAEDepoisDeSairDeixaDeVer() throws Exception {
        Cookie sessao = ApoioTeste.login(mvc, cenario.admin.getEmail(), CenarioParoquia.SENHA);

        mvc.perform(post("/api/plataforma/paroquias/{id}/assumir", cenario.paroquiaA.getId()).cookie(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/celebracoes").cookie(sessao).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .contains("\"id\":" + celebracaoA.getId()));
        mvc.perform(get("/api/celebracoes/{id}", celebracaoA.getId()).cookie(sessao).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/plataforma/paroquias/sair").cookie(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/celebracoes").cookie(sessao).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain("\"id\":" + celebracaoA.getId()));
        mvc.perform(get("/api/celebracoes/{id}", celebracaoA.getId()).cookie(sessao).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void auditoriaRegistraAssumirEscritaESair() throws Exception {
        Cookie sessao = ApoioTeste.login(mvc, cenario.admin.getEmail(), CenarioParoquia.SENHA);

        mvc.perform(post("/api/plataforma/paroquias/{id}/assumir", cenario.paroquiaA.getId()).cookie(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        String corpo = json(new CelebracaoRequestDTO(comunidade.getId(), LocalDate.now().plusDays(27), LocalTime.of(9, 0), null, null, null));
        mvc.perform(post("/api/celebracoes").cookie(sessao).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/plataforma/paroquias/sair").cookie(sessao).with(csrf()))
                .andExpect(status().isNoContent());

        var logs = auditLogRepository.findByParoquiaId(cenario.paroquiaA.getId());
        assertThat(logs).extracting(AuditLog::getTipoEvento).contains(
                "ADMIN_ASSUMIU_PAROQUIA", "ADMIN_ACAO_EM_PAROQUIA_ASSUMIDA", "ADMIN_SAIU_PAROQUIA");
        assertThat(logs).allSatisfy(log -> assertThat(log.getUsuario().getId()).isEqualTo(cenario.admin.getId()));

        AuditLog escrita = logs.stream().filter(l -> l.getTipoEvento().equals("ADMIN_ACAO_EM_PAROQUIA_ASSUMIDA")).findFirst().orElseThrow();
        assertThat(escrita.getDetalhe()).contains("POST").contains("/api/celebracoes");
    }
}
