package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.HorarioMissa;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.HorarioMissaRepository;
import br.com.zep.servio.repository.ModeloVagaRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.CoberturaAutomaticaService;
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
import java.time.LocalTime;
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

/**
 * Horários fixos de missa e a escolha de cobertura de cada pastoral (Sprint 4, cartão 1). Os horários reais da
 * paróquia não aparecem no código: cada teste cadastra os seus.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class HorarioMissaIT {

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
    ModeloVagaRepository modeloVagaRepository;
    @Autowired
    HorarioMissaRepository horarioMissaRepository;
    @Autowired
    CoberturaAutomaticaService coberturaAutomaticaService;
    @Autowired
    PasswordEncoder passwordEncoder;

    CenarioParoquia cenario;
    Comunidade matriz;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        matriz = new Comunidade();
        matriz.setNome("Matriz");
        matriz.setParoquiaId(cenario.paroquiaA.getId());
        matriz = comunidadeRepository.save(matriz);
    }

    private ResultActions como(Usuario quem, MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao.with(user(cenario.principal(quem))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON));
    }

    private static String corpo(String hora, Integer dia) {
        return "{" + (dia == null ? "" : "\"diaSemana\":" + dia + ",") + "\"hora\":\"" + hora + "\"}";
    }

    private Long criarHorario(String hora) throws Exception {
        String resposta = como(cenario.padreA, post("/api/comunidades/{id}/horarios-missa", matriz.getId())
                .content(corpo(hora, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(resposta).read("$.id", Long.class);
    }

    @Test
    void padreCriaEditaEDesativaHorario() throws Exception {
        Long id = criarHorario("10:00");

        como(cenario.padreA, get("/api/comunidades/{id}/horarios-missa", matriz.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].diaSemana").value(7))
                .andExpect(jsonPath("$[0].hora").value("10:00:00"));

        como(cenario.padreA, put("/api/comunidades/{c}/horarios-missa/{id}", matriz.getId(), id)
                .content(corpo("11:00", 6)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaSemana").value(6))
                .andExpect(jsonPath("$.hora").value("11:00:00"));

        como(cenario.padreA, delete("/api/comunidades/{c}/horarios-missa/{id}", matriz.getId(), id))
                .andExpect(status().isNoContent());
        como(cenario.padreA, get("/api/comunidades/{id}/horarios-missa", matriz.getId()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void coordenadorNaoCriaHorarioEComunidadeDeOutraParoquiaDa404() throws Exception {
        como(cenario.coordenadorPascom, post("/api/comunidades/{id}/horarios-missa", matriz.getId())
                .content(corpo("10:00", null)))
                .andExpect(status().isForbidden());

        Comunidade deOutra = new Comunidade();
        deOutra.setNome("Outra");
        deOutra.setParoquiaId(cenario.paroquiaB.getId());
        deOutra = comunidadeRepository.save(deOutra);
        como(cenario.padreA, post("/api/comunidades/{id}/horarios-missa", deOutra.getId()).content(corpo("10:00", null)))
                .andExpect(status().isNotFound());
        como(cenario.padreA, get("/api/comunidades/{id}/horarios-missa", deOutra.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void horarioRepetidoDa409EDiaForaDeIntervaloOuCampoExtraDa400() throws Exception {
        criarHorario("10:00");

        como(cenario.padreA, post("/api/comunidades/{id}/horarios-missa", matriz.getId()).content(corpo("10:00", null)))
                .andExpect(status().isConflict());
        como(cenario.padreA, post("/api/comunidades/{id}/horarios-missa", matriz.getId()).content(corpo("09:00", 0)))
                .andExpect(status().isBadRequest());
        como(cenario.padreA, post("/api/comunidades/{id}/horarios-missa", matriz.getId()).content(corpo("09:00", 8)))
                .andExpect(status().isBadRequest());
        como(cenario.padreA, post("/api/comunidades/{id}/horarios-missa", matriz.getId())
                .content("{\"hora\":\"09:00\",\"paroquiaId\":99}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void coordenadorEscolheUmHorarioPorComunidadeECoordenadorDeOutraPastoralNaoMexe() throws Exception {
        Long dez = criarHorario("10:00");
        Long dezoito = criarHorario("18:00");

        como(cenario.coordenadorPascom, put("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId())
                .content("{\"horarioMissaIds\":[" + dez + "]}"))
                .andExpect(status().isOk());
        como(cenario.coordenadorPascom, get("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.horarioMissaId == " + dez + ")].cobertura").value(true))
                .andExpect(jsonPath("$[?(@.horarioMissaId == " + dezoito + ")].cobertura").value(false));

        como(cenario.coordenadorPascom, put("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId())
                .content("{\"horarioMissaIds\":[" + dez + "," + dezoito + "]}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Escolha um horário por comunidade"));

        // trocar a escolha desfaz a anterior
        como(cenario.coordenadorPascom, put("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId())
                .content("{\"horarioMissaIds\":[" + dezoito + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.horarioMissaId == " + dez + ")].cobertura").value(false))
                .andExpect(jsonPath("$[?(@.horarioMissaId == " + dezoito + ")].cobertura").value(true));

        como(cenario.coordenadorEcc, put("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId())
                .content("{\"horarioMissaIds\":[" + dez + "]}"))
                .andExpect(status().isNotFound());
        como(cenario.membro1Pascom, put("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId())
                .content("{\"horarioMissaIds\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void desativarOHorarioLimpaAEscolhaDaPastoral() throws Exception {
        Long dez = criarHorario("10:00");
        como(cenario.coordenadorPascom, put("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId())
                .content("{\"horarioMissaIds\":[" + dez + "]}")).andExpect(status().isOk());

        como(cenario.padreA, delete("/api/comunidades/{c}/horarios-missa/{id}", matriz.getId(), dez))
                .andExpect(status().isNoContent());

        como(cenario.coordenadorPascom, get("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void semLoginDa401() throws Exception {
        mvc.perform(get("/api/comunidades/{id}/horarios-missa", matriz.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId()))
                .andExpect(status().isUnauthorized());
    }

    private Celebracao celebracaoDe(HorarioMissa horario, int diasAFrente) {
        Celebracao celebracao = new Celebracao();
        celebracao.setComunidade(matriz);
        celebracao.setData(LocalDate.now().plusDays(diasAFrente));
        celebracao.setHora(horario != null ? horario.getHora() : LocalTime.of(10, 0));
        celebracao.setTipo(TipoCelebracao.MISSA_DOMINICAL);
        celebracao.setHorarioMissa(horario);
        celebracao.setParoquiaId(cenario.paroquiaA.getId());
        return celebracaoRepository.save(celebracao);
    }

    private HorarioMissa horarioDireto(String hora) {
        HorarioMissa horario = new HorarioMissa();
        horario.setComunidade(matriz);
        horario.setHora(LocalTime.parse(hora));
        horario.setParoquiaId(cenario.paroquiaA.getId());
        return horarioMissaRepository.save(horario);
    }

    private void ligarCoberturaComModelo(br.com.zep.servio.model.Pastoral pastoral, Funcao funcao) throws Exception {
        como(cenario.padreA, put("/api/pastorais/{id}/config/COBERTURA_AUTOMATICA", pastoral.getId())
                .content("{\"ativa\":true,\"parametros\":{}}")).andExpect(status().isOk());
        como(cenario.padreA, post("/api/pastorais/{id}/modelos-vaga", pastoral.getId())
                .content("{\"funcaoId\":" + funcao.getId() + ",\"tipoCelebracao\":\"MISSA_DOMINICAL\",\"quantidade\":2}"))
                .andExpect(status().isCreated());
    }

    private Funcao funcao(br.com.zep.servio.model.Pastoral pastoral, String nome) {
        Funcao funcao = new Funcao();
        funcao.setNome(nome);
        funcao.setPastoral(pastoral);
        funcao.setParoquiaId(cenario.paroquiaA.getId());
        return funcaoRepository.save(funcao);
    }

    @Test
    void celebracaoDeHorarioFixoSoDaVagaAQuemCobreOHorario() throws Exception {
        Funcao funcaoPascom = funcao(cenario.pascom, "Fotografia");
        Funcao funcaoEcc = funcao(cenario.ecc, "Recepção");
        ligarCoberturaComModelo(cenario.pascom, funcaoPascom);
        ligarCoberturaComModelo(cenario.ecc, funcaoEcc);
        HorarioMissa dez = horarioDireto("10:00");
        HorarioMissa dezoito = horarioDireto("18:00");
        como(cenario.coordenadorPascom, put("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId())
                .content("{\"horarioMissaIds\":[" + dez.getId() + "]}")).andExpect(status().isOk());
        como(cenario.coordenadorEcc, put("/api/pastorais/{id}/cobertura-missa", cenario.ecc.getId())
                .content("{\"horarioMissaIds\":[" + dezoito.getId() + "]}")).andExpect(status().isOk());

        Celebracao missaDasDez = celebracaoDe(dez, 100);
        coberturaAutomaticaService.aplicarNaCriacao(missaDasDez, cenario.paroquiaA.getId());

        List<Vaga> vagas = vagaRepository.findByCelebracaoIdAndActiveTrue(missaDasDez.getId());
        assertThat(vagas).singleElement().satisfies(v ->
                assertThat(v.getFuncao().getPastoral().getId()).isEqualTo(cenario.pascom.getId()));
    }

    @Test
    void pastoralSemEscolhaNaoRecebeVagaNasGeradasMasRecebeNasCriadasAMao() throws Exception {
        Funcao funcaoPascom = funcao(cenario.pascom, "Fotografia");
        ligarCoberturaComModelo(cenario.pascom, funcaoPascom);
        HorarioMissa dez = horarioDireto("10:00");

        Celebracao gerada = celebracaoDe(dez, 101);
        coberturaAutomaticaService.aplicarNaCriacao(gerada, cenario.paroquiaA.getId());
        Celebracao aMao = celebracaoDe(null, 102);
        coberturaAutomaticaService.aplicarNaCriacao(aMao, cenario.paroquiaA.getId());

        assertThat(vagaRepository.findByCelebracaoIdAndActiveTrue(gerada.getId())).isEmpty();
        assertThat(vagaRepository.findByCelebracaoIdAndActiveTrue(aMao.getId())).hasSize(1);
    }

    @Test
    void celebracaoMostraDeQualHorarioNasceu() throws Exception {
        HorarioMissa dez = horarioDireto("10:00");
        Celebracao gerada = celebracaoDe(dez, 103);

        como(cenario.padreA, get("/api/celebracoes/{id}", gerada.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarioMissaId").value(dez.getId()));
        como(cenario.padreA, get("/api/celebracoes/{id}", celebracaoDe(null, 104).getId()))
                .andExpect(jsonPath("$.horarioMissaId").doesNotExist());
    }
}
