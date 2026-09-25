package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.dto.LancamentoFinanceiroRequestDTO;
import br.com.zep.servio.model.enumerated.TipoLancamento;
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

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Parte 5.8: caixa da pastoral (saldo, exclusão lógica não conta, validação de valor) e dashboard consolidado do padre. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class FinanceiroIT {

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
    void saldoEIgualEntradasMenosSaidas() throws Exception {
        mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, new BigDecimal("100.00"), "Doação", LocalDate.now()))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LancamentoFinanceiroRequestDTO(TipoLancamento.SAIDA, new BigDecimal("30.00"), "Material", LocalDate.now()))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/pastorais/{id}/financeiro/saldo", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntradas").value(100.00))
                .andExpect(jsonPath("$.totalSaidas").value(30.00))
                .andExpect(jsonPath("$.saldo").value(70.00));
    }

    @Test
    void lancamentoDesativadoNaoConta() throws Exception {
        String resposta = mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, new BigDecimal("50.00"), "Doação", LocalDate.now()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long idParaDesativar = objectMapper.readTree(resposta).get("id").asLong();
        mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, new BigDecimal("20.00"), "Doação", LocalDate.now()))))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/pastorais/{id}/financeiro/{lid}", cenario.pascom.getId(), idParaDesativar)
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/pastorais/{id}/financeiro/saldo", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntradas").value(20.00))
                .andExpect(jsonPath("$.saldo").value(20.00));
    }

    @Test
    void valorMenorOuIgualZeroDaBadRequest() throws Exception {
        mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, BigDecimal.ZERO, "Doação", LocalDate.now()))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, new BigDecimal("-5.00"), "Doação", LocalDate.now()))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dashboardDoPadreBateComSomaPorPastoral() throws Exception {
        mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.tesoureiroPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, new BigDecimal("100.00"), "Doação Pascom", LocalDate.now()))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.ecc.getId())
                        .with(user(cenario.principal(cenario.tesoureiroEcc))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, new BigDecimal("50.00"), "Doação ECC", LocalDate.now()))))
                .andExpect(status().isCreated());

        String de = LocalDate.now().minusDays(1).toString();
        String ate = LocalDate.now().plusDays(1).toString();
        mvc.perform(get("/api/financeiro/resumo?de={de}&ate={ate}", de, ate)
                        .with(user(cenario.principal(cenario.padreA))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEntradas").value(150.00))
                .andExpect(jsonPath("$.saldo").value(150.00))
                .andExpect(jsonPath("$.porPastoral[?(@.pastoralId == " + cenario.pascom.getId() + ")].totalEntradas").value(100.00))
                .andExpect(jsonPath("$.porPastoral[?(@.pastoralId == " + cenario.ecc.getId() + ")].totalEntradas").value(50.00));
    }
}
