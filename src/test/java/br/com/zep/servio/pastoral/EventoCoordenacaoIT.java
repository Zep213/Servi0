package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Coordenação cria, edita e cancela eventos da própria pastoral (Sprint 4, cartão 3). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class EventoCoordenacaoIT {

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
    PasswordEncoder passwordEncoder;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Comunidade matriz;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        matriz = escala.comunidade(cenario.paroquiaA);
    }

    private ResultActions como(Usuario quem, MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao.with(user(cenario.principal(quem))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON));
    }

    private String corpo(LocalDate data, String titulo) {
        return "{\"comunidadeId\":" + matriz.getId() + ",\"data\":\"" + data + "\",\"hora\":\"15:00\""
                + (titulo == null ? "" : ",\"titulo\":\"" + titulo + "\"") + "}";
    }

    private ResultActions criar(Usuario quem, Long pastoralId, LocalDate data) throws Exception {
        return como(quem, post("/api/pastorais/{id}/eventos", pastoralId).content(corpo(data, "Retiro")));
    }

    private Long idDe(ResultActions resposta) throws Exception {
        return JsonPath.parse(resposta.andReturn().getResponse().getContentAsString()).read("$.id", Long.class);
    }

    private Funcao funcao(Pastoral pastoral, String nome) {
        Funcao funcao = new Funcao();
        funcao.setNome(nome);
        funcao.setPastoral(pastoral);
        funcao.setParoquiaId(cenario.paroquiaA.getId());
        return funcaoRepository.save(funcao);
    }

    private void ligarCoberturaComModeloDeEvento(Pastoral pastoral, Funcao funcao) throws Exception {
        como(cenario.padreA, put("/api/pastorais/{id}/config/COBERTURA_AUTOMATICA", pastoral.getId())
                .content("{\"ativa\":true,\"parametros\":{}}")).andExpect(status().isOk());
        como(cenario.padreA, post("/api/pastorais/{id}/modelos-vaga", pastoral.getId())
                .content("{\"funcaoId\":" + funcao.getId() + ",\"tipoCelebracao\":\"EVENTO\",\"quantidade\":2}"))
                .andExpect(status().isCreated());
    }

    @Test
    void coordenadorCriaEventoDaPastoralComVagasSoDela() throws Exception {
        ligarCoberturaComModeloDeEvento(cenario.pascom, funcao(cenario.pascom, "Fotografia"));
        ligarCoberturaComModeloDeEvento(cenario.ecc, funcao(cenario.ecc, "Recepção"));

        ResultActions resposta = criar(cenario.coordenadorPascom, cenario.pascom.getId(), LocalDate.now().plusDays(10))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("EVENTO"))
                .andExpect(jsonPath("$.titulo").value("Retiro"))
                .andExpect(jsonPath("$.pastoralOrigemId").value(cenario.pascom.getId()));

        List<Vaga> vagas = vagaRepository.findByCelebracaoIdAndActiveTrue(idDe(resposta));
        assertThat(vagas).singleElement().satisfies(v ->
                assertThat(v.getFuncao().getPastoral().getId()).isEqualTo(cenario.pascom.getId()));
    }

    @Test
    void viceMembroECoordenadorDeOutraPastoralNaoCriamEPadreCria() throws Exception {
        LocalDate data = LocalDate.now().plusDays(10);
        criar(cenario.vicePascom, cenario.pascom.getId(), data).andExpect(status().isForbidden());
        criar(cenario.membro1Pascom, cenario.pascom.getId(), data).andExpect(status().isForbidden());
        // coordenador do ECC não enxerga a Pascom: 404, como se ela não existisse
        criar(cenario.coordenadorEcc, cenario.pascom.getId(), data).andExpect(status().isNotFound());
        criar(cenario.padreB, cenario.pascom.getId(), data).andExpect(status().isNotFound());
        criar(cenario.padreA, cenario.pascom.getId(), data).andExpect(status().isCreated());
    }

    @Test
    void dataPassadaTituloEComunidadeInvalidos() throws Exception {
        criar(cenario.coordenadorPascom, cenario.pascom.getId(), LocalDate.now().minusDays(1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A data do evento já passou"));
        como(cenario.coordenadorPascom, post("/api/pastorais/{id}/eventos", cenario.pascom.getId())
                .content(corpo(LocalDate.now().plusDays(5), null))).andExpect(status().isBadRequest());

        Comunidade deOutra = escala.comunidade(cenario.paroquiaB);
        como(cenario.coordenadorPascom, post("/api/pastorais/{id}/eventos", cenario.pascom.getId())
                .content("{\"comunidadeId\":" + deOutra.getId() + ",\"data\":\"" + LocalDate.now().plusDays(5)
                        + "\",\"hora\":\"15:00\",\"titulo\":\"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void campoAMaisNoCorpoDa400() throws Exception {
        como(cenario.coordenadorPascom, post("/api/pastorais/{id}/eventos", cenario.pascom.getId())
                .content("{\"comunidadeId\":" + matriz.getId() + ",\"data\":\"" + LocalDate.now().plusDays(5)
                        + "\",\"hora\":\"15:00\",\"titulo\":\"X\",\"tipo\":\"MISSA_DOMINICAL\",\"pastoralOrigemId\":2}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coordenadorEditaECancelaOProprioEvento() throws Exception {
        Long id = idDe(criar(cenario.coordenadorPascom, cenario.pascom.getId(), LocalDate.now().plusDays(10)));
        LocalDate novaData = LocalDate.now().plusDays(20);

        como(cenario.coordenadorPascom, put("/api/pastorais/{p}/eventos/{id}", cenario.pascom.getId(), id)
                .content(corpo(novaData, "Retiro de Advento")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titulo").value("Retiro de Advento"))
                .andExpect(jsonPath("$.data").value(novaData.toString()))
                .andExpect(jsonPath("$.tipo").value("EVENTO"));

        como(cenario.coordenadorPascom, delete("/api/pastorais/{p}/eventos/{id}", cenario.pascom.getId(), id))
                .andExpect(status().isNoContent());
        assertThat(celebracaoRepository.findById(id).orElseThrow().isActive()).isFalse();
    }

    @Test
    void eventoDeOutraPastoralOuDoPadreOuMissaDa404() throws Exception {
        Long daPascom = idDe(criar(cenario.coordenadorPascom, cenario.pascom.getId(), LocalDate.now().plusDays(10)));
        Long doPadre = idDe(mvc.perform(post("/api/celebracoes")
                .with(user(cenario.principal(cenario.padreA))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comunidadeId\":" + matriz.getId() + ",\"data\":\"" + LocalDate.now().plusDays(11)
                        + "\",\"hora\":\"10:00\",\"tipo\":\"EVENTO\",\"titulo\":\"Quermesse\"}"))
                .andExpect(status().isCreated()));

        // coordenador da Pascom não mexe no evento do padre; o do ECC não vê nem a Pascom
        como(cenario.coordenadorPascom, delete("/api/pastorais/{p}/eventos/{id}", cenario.pascom.getId(), doPadre))
                .andExpect(status().isNotFound());
        como(cenario.coordenadorEcc, delete("/api/pastorais/{p}/eventos/{id}", cenario.ecc.getId(), daPascom))
                .andExpect(status().isNotFound());
        como(cenario.coordenadorPascom, delete("/api/pastorais/{p}/eventos/{id}", cenario.pascom.getId(), 999_999L))
                .andExpect(status().isNotFound());
    }

    @Test
    void eventoComGenteEscaladaNaoEditaNemCancelaMasLiberaDepoisQueTodosRecusam() throws Exception {
        Long id = idDe(criar(cenario.coordenadorPascom, cenario.pascom.getId(), LocalDate.now().plusDays(10)));
        Vaga vaga = escala.vaga(cenario.paroquiaA, celebracaoRepository.findById(id).orElseThrow(),
                cenario.funcaoComunicacaoPascom, 2);
        Alocacao pendente = escala.alocacao(cenario.paroquiaA, vaga, cenario.membro1Pascom, StatusConvite.PENDENTE);

        como(cenario.coordenadorPascom, put("/api/pastorais/{p}/eventos/{id}", cenario.pascom.getId(), id)
                .content(corpo(LocalDate.now().plusDays(12), "Outro nome")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Há pessoas escaladas neste evento: peça ao padre"));
        como(cenario.coordenadorPascom, delete("/api/pastorais/{p}/eventos/{id}", cenario.pascom.getId(), id))
                .andExpect(status().isUnprocessableEntity());
        assertThat(celebracaoRepository.findById(id).orElseThrow().isActive()).isTrue();

        pendente.setStatus(StatusConvite.RECUSADA);
        alocacaoRepository.save(pendente);
        como(cenario.coordenadorPascom, delete("/api/pastorais/{p}/eventos/{id}", cenario.pascom.getId(), id))
                .andExpect(status().isNoContent());
    }

    @Test
    void padreContinuaCriandoEventoPeloCaminhoDeSempreSemPastoralDeOrigem() throws Exception {
        como(cenario.padreA, post("/api/celebracoes")
                .content("{\"comunidadeId\":" + matriz.getId() + ",\"data\":\"" + LocalDate.now().plusDays(11)
                        + "\",\"hora\":\"10:00\",\"tipo\":\"EVENTO\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pastoralOrigemId").doesNotExist());
        como(cenario.coordenadorPascom, post("/api/celebracoes")
                .content("{\"comunidadeId\":" + matriz.getId() + ",\"data\":\"" + LocalDate.now().plusDays(11)
                        + "\",\"hora\":\"10:00\",\"tipo\":\"EVENTO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void soQuemTemVagaNoEventoOEnxerga() throws Exception {
        ligarCoberturaComModeloDeEvento(cenario.pascom, funcao(cenario.pascom, "Fotografia"));
        Long id = idDe(criar(cenario.coordenadorPascom, cenario.pascom.getId(), LocalDate.now().plusDays(10)));

        como(cenario.coordenadorPascom, get("/api/celebracoes/{id}", id)).andExpect(status().isOk());
        como(cenario.membroEcc, get("/api/celebracoes/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void semLoginDa401() throws Exception {
        mvc.perform(post("/api/pastorais/{id}/eventos", cenario.pascom.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo(LocalDate.now().plusDays(5), "X")))
                .andExpect(status().isUnauthorized());
    }
}
