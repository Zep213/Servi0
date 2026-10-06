package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.AlteracaoPendenteRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.notification.Notificador;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static br.com.zep.servio.pastoral.CenarioEscalacao.domingo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Escalação manual (Etapa 6, Parte 2): motivos dos candidatos, forçar só para coordenação,
 * substituição (inclusive pelo vice, que gera alteração pendente), o caminho único do
 * POST /api/alocacoes e o limite de reenvio de convite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class EscalacaoManualIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
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
    AlteracaoPendenteRepository alteracaoPendenteRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Comunidade comunidade;
    Celebracao celebracao;
    Vaga vaga;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        comunidade = escala.comunidade(cenario.paroquiaA);

        // membro1 serviu no domingo anterior: a regra de domingos seguidos o impede nesta vaga
        Celebracao anterior = escala.celebracao(cenario.paroquiaA, comunidade, domingo(4), TipoCelebracao.MISSA_DOMINICAL);
        escala.alocacao(cenario.paroquiaA, escala.vaga(cenario.paroquiaA, anterior, cenario.funcaoComunicacaoPascom, 1),
                cenario.membro1Pascom, StatusConvite.ACEITA);

        celebracao = escala.celebracao(cenario.paroquiaA, comunidade, domingo(5), TipoCelebracao.MISSA_DOMINICAL);
        vaga = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 2);
    }

    private ResultActions como(Usuario quem, MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao.with(user(cenario.principal(quem))).with(csrf()));
    }

    private ResultActions escalar(Usuario quem, Vaga alvo, Usuario convidado, boolean forcar) throws Exception {
        return como(quem, post("/api/vagas/{id}/escalar", alvo.getId())
                .contentType("application/json")
                .content("{\"usuarioId\":" + convidado.getId() + ",\"forcar\":" + forcar + "}"));
    }

    private Long idDaAlocacaoCriada(ResultActions resultado) throws Exception {
        MvcResult r = resultado.andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private Alocacao alocacao(Long id) {
        return alocacaoRepository.findById(id).orElseThrow();
    }

    @Test
    void candidatosMostramOMotivoDeQuemEstaImpedido() throws Exception {
        MvcResult r = como(cenario.coordenadorPascom, get("/api/vagas/{id}/candidatos", vaga.getId())).andExpect(status().isOk()).andReturn();
        JsonNode candidatos = json.readTree(r.getResponse().getContentAsString());

        JsonNode membro1 = encontrar(candidatos, cenario.membro1Pascom);
        JsonNode membro2 = encontrar(candidatos, cenario.membro2Pascom);
        assertThat(membro1.get("elegivel").asBoolean()).isFalse();
        assertThat(membro1.get("motivos").get(0).asText()).contains("domingo");
        assertThat(membro2.get("elegivel").asBoolean()).isTrue();
        assertThat(membro2.get("motivos")).isEmpty();
    }

    private JsonNode encontrar(JsonNode lista, Usuario usuario) {
        for (JsonNode item : lista) {
            if (item.get("usuario").get("id").asLong() == usuario.getId()) {
                return item;
            }
        }
        throw new AssertionError("candidato " + usuario.getId() + " não está na lista");
    }

    @Test
    void quemRecusouAVagaContinuaPodendoSerEscaladoAMao() throws Exception {
        // regra já existente: quem recusou pode ser convidado de novo (o sorteio é que não o escolhe de novo)
        escala.alocacao(cenario.paroquiaA, vaga, cenario.membro2Pascom, StatusConvite.RECUSADA);

        MvcResult r = como(cenario.coordenadorPascom, get("/api/vagas/{id}/candidatos", vaga.getId())).andReturn();
        assertThat(encontrar(json.readTree(r.getResponse().getContentAsString()), cenario.membro2Pascom)
                .get("elegivel").asBoolean()).isTrue();

        escalar(cenario.coordenadorPascom, vaga, cenario.membro2Pascom, false).andExpect(status().isCreated());
    }

    @Test
    void impedidoSemForcarDa422() throws Exception {
        escalar(cenario.coordenadorPascom, vaga, cenario.membro1Pascom, false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("domingo")));
    }

    @Test
    void coordenadorForcaEGravaOrigemQuemEscalouEToken() throws Exception {
        Long id = idDaAlocacaoCriada(escalar(cenario.coordenadorPascom, vaga, cenario.membro1Pascom, true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origem").value("COORDENADOR")));

        Alocacao criada = alocacao(id);
        assertThat(criada.getEscaladoPor().getId()).isEqualTo(cenario.coordenadorPascom.getId());
        assertThat(criada.getTokenHash()).hasSize(64);
        assertThat(criada.getStatus()).isEqualTo(StatusConvite.PENDENTE);
        assertThat(criada.getDataLimiteResposta()).isNotNull();
    }

    @Test
    void viceNuncaForca() throws Exception {
        escalar(cenario.vicePascom, vaga, cenario.membro1Pascom, true).andExpect(status().isForbidden());
    }

    @Test
    void viceEscalaQuemNaoEstaImpedidoSemForcar() throws Exception {
        escalar(cenario.vicePascom, vaga, cenario.membro2Pascom, false).andExpect(status().isCreated());
    }

    @Test
    void duplicadaDa409() throws Exception {
        escalar(cenario.coordenadorPascom, vaga, cenario.membro2Pascom, false).andExpect(status().isCreated());
        escalar(cenario.coordenadorPascom, vaga, cenario.membro2Pascom, false).andExpect(status().isConflict());
    }

    @Test
    void vagaCheiaDa422() throws Exception {
        Celebracao cheia = escala.celebracao(cenario.paroquiaA, comunidade, domingo(7), TipoCelebracao.MISSA_DOMINICAL);
        Vaga vagaCheia = escala.vaga(cenario.paroquiaA, cheia, cenario.funcaoComunicacaoPascom, 1);
        escala.alocacao(cenario.paroquiaA, vagaCheia, cenario.secretario2Pascom, StatusConvite.ACEITA);

        escalar(cenario.coordenadorPascom, vagaCheia, cenario.tesoureiroPascom, false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("todas as posições")));
    }

    @Test
    void escalarVagaDeOutraPastoralDa404() throws Exception {
        escalar(cenario.coordenadorEcc, vaga, cenario.membroEcc, false).andExpect(status().isNotFound());
    }

    @Test
    void substituirMarcaAAntigaComoSubstituidaESoNovaFicaPendente() throws Exception {
        Long antigaId = idDaAlocacaoCriada(escalar(cenario.coordenadorPascom, vaga, cenario.membro2Pascom, false));

        Long novaId = idDaAlocacaoCriada(como(cenario.coordenadorPascom, post("/api/alocacoes/{id}/substituir", antigaId)
                .contentType("application/json")
                .content("{\"usuarioId\":" + cenario.secretario2Pascom.getId() + ",\"forcar\":false}"))
                .andExpect(status().isCreated()));

        Alocacao antiga = alocacao(antigaId);
        assertThat(antiga.getStatus()).isEqualTo(StatusConvite.SUBSTITUIDA);
        assertThat(antiga.getSubstituidaEm()).isNotNull();
        assertThat(antiga.getTokenHash()).isNull();

        Alocacao nova = alocacao(novaId);
        assertThat(nova.getStatus()).isEqualTo(StatusConvite.PENDENTE);
        assertThat(nova.getUsuario().getId()).isEqualTo(cenario.secretario2Pascom.getId());
        assertThat(nova.getTokenHash()).hasSize(64);
    }

    @Test
    void substituicaoPeloViceGeraAlteracaoPendente() throws Exception {
        Long antigaId = idDaAlocacaoCriada(escalar(cenario.coordenadorPascom, vaga, cenario.membro2Pascom, false));

        Long novaId = idDaAlocacaoCriada(como(cenario.vicePascom, post("/api/alocacoes/{id}/substituir", antigaId)
                .contentType("application/json")
                .content("{\"usuarioId\":" + cenario.secretario2Pascom.getId() + ",\"forcar\":false}"))
                .andExpect(status().isCreated()));

        List<br.com.zep.servio.model.AlteracaoPendente> pendentes = alteracaoPendenteRepository.findByAlocacaoId(novaId);
        assertThat(pendentes).hasSize(1);
        assertThat(pendentes.get(0).getStatus()).isEqualTo(StatusAlteracaoPendente.PENDENTE);
    }

    @Test
    void postAlocacoesAgoraGeraTokenEOrigemPeloMesmoCaminho() throws Exception {
        Long id = idDaAlocacaoCriada(como(cenario.coordenadorPascom, post("/api/alocacoes")
                .contentType("application/json")
                .content("{\"vagaId\":" + vaga.getId() + ",\"usuarioId\":" + cenario.membro2Pascom.getId() + "}"))
                .andExpect(status().isCreated()));

        Alocacao criada = alocacao(id);
        assertThat(criada.getTokenHash()).hasSize(64);
        assertThat(criada.getEscaladoPor().getId()).isEqualTo(cenario.coordenadorPascom.getId());
    }

    @Test
    void reenvioDeConviteTrocaOTokenMantemOPrazoEMaximoUmPorHora() throws Exception {
        Long id = idDaAlocacaoCriada(escalar(cenario.coordenadorPascom, vaga, cenario.membro2Pascom, false));
        Alocacao antes = alocacao(id);
        String tokenAntes = antes.getTokenHash();

        como(cenario.coordenadorPascom, post("/api/alocacoes/{id}/reenviar-convite", id))
                .andExpect(status().isOk());

        Alocacao depois = alocacao(id);
        assertThat(depois.getTokenHash()).isNotEqualTo(tokenAntes);
        assertThat(depois.getDataLimiteResposta()).isEqualTo(antes.getDataLimiteResposta());

        como(cenario.coordenadorPascom, post("/api/alocacoes/{id}/reenviar-convite", id))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reenviarConviteDeQuemNaoEstaPendenteDa422() throws Exception {
        Alocacao aceita = escala.alocacao(cenario.paroquiaA, vaga, cenario.secretario1Pascom, StatusConvite.ACEITA);

        como(cenario.coordenadorPascom, post("/api/alocacoes/{id}/reenviar-convite", aceita.getId()))
                .andExpect(status().isUnprocessableEntity());
    }
}
