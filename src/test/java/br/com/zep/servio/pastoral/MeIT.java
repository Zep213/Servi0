package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.*;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.notification.Notificador;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET e PUT /api/me: telefone, aparência, pedido de telefone e pendências por pastoral. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class MeIT {

    @Autowired
    MockMvc mvc;
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
    AlocacaoRepository alocacaoRepository;
    @Autowired
    AuditLogRepository auditLogRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
    }

    private ResultActions getMe(Usuario quem) throws Exception {
        return mvc.perform(get("/api/me").with(user(cenario.principal(quem))));
    }

    private ResultActions putMe(Usuario quem, String json) throws Exception {
        return mvc.perform(put("/api/me").with(user(cenario.principal(quem))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void getTrazTelefoneAparenciaEPedirTelefone() throws Exception {
        getMe(cenario.membro1Pascom)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").doesNotExist())
                .andExpect(jsonPath("$.fotoUrl").doesNotExist())
                .andExpect(jsonPath("$.aparencia").value("AUTOMATICO"))
                .andExpect(jsonPath("$.pedirTelefone").value(true));
    }

    @Test
    void putNormalizaOTelefoneEParaDePedir() throws Exception {
        putMe(cenario.membro1Pascom, "{\"telefone\":\"(83) 9 8812-4407\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("+5583988124407"))
                .andExpect(jsonPath("$.pedirTelefone").value(false));

        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getTipoEvento()).isEqualTo("TELEFONE_ALTERADO");
            assertThat(log.getDetalhe()).contains("+55839****4407").doesNotContain("988124407");
        });
    }

    @Test
    void telefoneDeOutraContaDa409Generico() throws Exception {
        putMe(cenario.membro1Pascom, "{\"telefone\":\"83988124407\"}").andExpect(status().isOk());

        putMe(cenario.membro2Pascom, "{\"telefone\":\"+55 83 98812-4407\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Este telefone não pode ser usado"));
    }

    @Test
    void telefoneInvalidoDa400() throws Exception {
        putMe(cenario.membro1Pascom, "{\"telefone\":\"12345\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Telefone inválido"));
    }

    @Test
    void telefoneGrandeDemaisDa400PelaValidacao() throws Exception {
        putMe(cenario.membro1Pascom, "{\"telefone\":\"" + "9".repeat(41) + "\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void sextaTentativaDeTelefoneDa429() throws Exception {
        for (int i = 0; i < 5; i++) {
            putMe(cenario.membro1Pascom, "{\"telefone\":\"12345\"}").andExpect(status().isBadRequest());
        }
        putMe(cenario.membro1Pascom, "{\"telefone\":\"83988124407\"}").andExpect(status().isTooManyRequests());
    }

    @Test
    void telefoneVazioOuNuloNaoApaga() throws Exception {
        putMe(cenario.membro1Pascom, "{\"telefone\":\"83988124407\"}").andExpect(status().isOk());

        putMe(cenario.membro1Pascom, "{\"telefone\":\"\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefone").value("+5583988124407"));
        putMe(cenario.membro1Pascom, "{\"telefone\":null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.telefone").value("+5583988124407"));
    }

    @Test
    void tresPulosParamDePedirOTelefone() throws Exception {
        for (int i = 0; i < 2; i++) {
            putMe(cenario.membro1Pascom, "{\"pularTelefone\":true}")
                    .andExpect(jsonPath("$.pedirTelefone").value(true));
        }
        putMe(cenario.membro1Pascom, "{\"pularTelefone\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pedirTelefone").value(false));
    }

    @Test
    void pularNaoContaQuandoJaTemTelefone() throws Exception {
        putMe(cenario.membro1Pascom, "{\"telefone\":\"83988124407\"}").andExpect(status().isOk());
        putMe(cenario.membro1Pascom, "{\"pularTelefone\":true}").andExpect(status().isOk());

        assertThat(usuarioRepository.findById(cenario.membro1Pascom.getId()).orElseThrow().getTelefonePulos())
                .isZero();
    }

    @Test
    void aparenciaEscuroPersiste() throws Exception {
        putMe(cenario.membro1Pascom, "{\"aparencia\":\"ESCURO\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.aparencia").value("ESCURO"));
        getMe(cenario.membro1Pascom).andExpect(jsonPath("$.aparencia").value("ESCURO"));
    }

    @Test
    void campoDesconhecidoDa400() throws Exception {
        putMe(cenario.membro1Pascom, "{\"telefone\":\"83988124407\",\"perfil\":\"ADMIN\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void semAutenticacaoDa401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void pendenciasDoCoordenadorSomamOPainelEDoMembroSaoOsConvitesDele() throws Exception {
        Celebracao celebracao = escala.celebracao(cenario.paroquiaA, escala.comunidade(cenario.paroquiaA),
                LocalDate.now().plusDays(10), TipoCelebracao.MISSA_DOMINICAL);
        Vaga vaga = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 2);
        // vence em 2h: entra em "convites vencendo" (janela padrão de 6h) do painel
        Alocacao vencendo = escala.alocacao(cenario.paroquiaA, vaga, cenario.membro1Pascom, StatusConvite.PENDENTE);
        vencendo.setDataLimiteResposta(LocalDateTime.now().plusHours(2));
        alocacaoRepository.save(vencendo);
        // convite longe do prazo: conta para o membro, não para o painel
        escala.alocacao(cenario.paroquiaA, vaga, cenario.membro2Pascom, StatusConvite.PENDENTE);

        getMe(cenario.coordenadorPascom).andExpect(jsonPath("$.pastorais[0].pendencias").value(1));
        getMe(cenario.membro1Pascom).andExpect(jsonPath("$.pastorais[0].pendencias").value(1));
        getMe(cenario.membro2Pascom).andExpect(jsonPath("$.pastorais[0].pendencias").value(1));
        getMe(cenario.coordenadorEcc).andExpect(jsonPath("$.pastorais[0].pendencias").value(0));
    }
}
