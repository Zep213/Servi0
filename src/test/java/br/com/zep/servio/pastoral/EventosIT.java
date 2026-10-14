package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.CelebracaoRequestDTO;
import br.com.zep.servio.model.dto.ModeloVagaRequestDTO;
import br.com.zep.servio.model.dto.PastoralConfigRequestDTO;
import br.com.zep.servio.model.dto.VagaRequestDTO;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoCelebracaoModelo;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parte 5.7: modelo de eventos — cobertura automática materializando vagas ao nascer a
 * celebração, tipo EVENTO usando modelo de EVENTO (ou TODOS), "responsabilizar" uma pastoral por
 * uma vaga sem quantidade, o gate de "pastoral já responsável pelo evento" para vaga adicional, e
 * aplicar-futuras.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class EventosIT {

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
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    Comunidade comunidade;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        comunidade = new Comunidade();
        comunidade.setNome("Comunidade Central");
        comunidade.setParoquiaId(cenario.paroquiaA.getId());
        comunidade = comunidadeRepository.save(comunidade);
    }

    private String json(Object dto) {
        return objectMapper.writeValueAsString(dto);
    }

    private Funcao criarFuncaoDireta(br.com.zep.servio.model.Pastoral pastoral, String nome) {
        Funcao funcao = new Funcao();
        funcao.setNome(nome);
        funcao.setPastoral(pastoral);
        funcao.setParoquiaId(cenario.paroquiaA.getId());
        return funcaoRepository.save(funcao);
    }

    private Celebracao criarCelebracaoDireta(LocalDate data) {
        Celebracao celebracao = new Celebracao();
        celebracao.setComunidade(comunidade);
        celebracao.setData(data);
        celebracao.setHora(LocalTime.of(10, 0));
        celebracao.setParoquiaId(cenario.paroquiaA.getId());
        return celebracaoRepository.save(celebracao);
    }

    private void ligarCoberturaAutomatica() throws Exception {
        mvc.perform(put("/api/pastorais/{id}/config/COBERTURA_AUTOMATICA", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PastoralConfigRequestDTO(true, Map.of()))))
                .andExpect(status().isOk());
    }

    private void criarModelo(Funcao funcao, TipoCelebracaoModelo tipo, int quantidade) throws Exception {
        mvc.perform(post("/api/pastorais/{id}/modelos-vaga", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ModeloVagaRequestDTO(funcao.getId(), tipo, quantidade))))
                .andExpect(status().isCreated());
    }

    @Test
    void coberturaAutomaticaCriaVagasComQuantidadeAoNascerCelebracaoMissaDominical() throws Exception {
        Funcao fotografia = criarFuncaoDireta(cenario.pascom, "Fotografia");
        Funcao transmissao = criarFuncaoDireta(cenario.pascom, "Transmissão");
        ligarCoberturaAutomatica();
        criarModelo(fotografia, TipoCelebracaoModelo.MISSA_DOMINICAL, 2);
        criarModelo(transmissao, TipoCelebracaoModelo.MISSA_DOMINICAL, 1);

        String corpo = json(new CelebracaoRequestDTO(comunidade.getId(), LocalDate.now().plusDays(50), LocalTime.of(10, 0), null, null, null));
        String resposta = mvc.perform(post("/api/celebracoes").with(user(cenario.principal(cenario.padreA))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long celebracaoId = objectMapper.readTree(resposta).get("id").asLong();

        List<Vaga> vagas = vagaRepository.findByCelebracaoIdAndActiveTrue(celebracaoId);
        assertThat(vagas).hasSize(2);
        assertThat(vagas).anySatisfy(v -> {
            assertThat(v.getFuncao().getId()).isEqualTo(fotografia.getId());
            assertThat(v.getQuantidade()).isEqualTo(2);
        });
        assertThat(vagas).anySatisfy(v -> {
            assertThat(v.getFuncao().getId()).isEqualTo(transmissao.getId());
            assertThat(v.getQuantidade()).isEqualTo(1);
        });
        // ECC não tem cobertura automática ligada (padrão desligada): nenhuma vaga dele nasce junto.
        assertThat(vagas).noneMatch(v -> v.getFuncao().getPastoral().getId().equals(cenario.ecc.getId()));
    }

    @Test
    void tipoEventoUsaModeloDeEventoOuTodosMasNaoModeloDeMissaDominical() throws Exception {
        Funcao funcaoTodos = criarFuncaoDireta(cenario.pascom, "Segurança");
        Funcao funcaoSoMissa = criarFuncaoDireta(cenario.pascom, "Leitura");
        ligarCoberturaAutomatica();
        criarModelo(funcaoTodos, TipoCelebracaoModelo.TODOS, 3);
        criarModelo(funcaoSoMissa, TipoCelebracaoModelo.MISSA_DOMINICAL, 5);

        String corpo = json(new CelebracaoRequestDTO(comunidade.getId(), LocalDate.now().plusDays(51), LocalTime.of(18, 0),
                null, TipoCelebracao.EVENTO, "Festa junina"));
        String resposta = mvc.perform(post("/api/celebracoes").with(user(cenario.principal(cenario.padreA))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long celebracaoId = objectMapper.readTree(resposta).get("id").asLong();

        List<Vaga> vagas = vagaRepository.findByCelebracaoIdAndActiveTrue(celebracaoId);
        assertThat(vagas).hasSize(1);
        assertThat(vagas.get(0).getFuncao().getId()).isEqualTo(funcaoTodos.getId());
        assertThat(vagas.get(0).getQuantidade()).isEqualTo(3);
    }

    @Test
    void padreResponsabilizaEccPelaBarraca_coordenadorDoEccDefineQuantidade_coordenadorDaPascomNaoVe() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(52));

        String corpoSemQuantidade = json(new VagaRequestDTO(celebracao.getId(), cenario.funcaoBarracaEcc.getId(), null, null, null));
        String resposta = mvc.perform(post("/api/vagas").with(user(cenario.principal(cenario.padreA))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoSemQuantidade))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quantidade").value(org.hamcrest.Matchers.nullValue()))
                .andReturn().getResponse().getContentAsString();
        Long vagaId = objectMapper.readTree(resposta).get("id").asLong();

        String corpoComQuantidade = json(new VagaRequestDTO(celebracao.getId(), cenario.funcaoBarracaEcc.getId(), 4, null, null));
        mvc.perform(put("/api/vagas/{id}", vagaId).with(user(cenario.principal(cenario.coordenadorEcc))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoComQuantidade))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantidade").value(4));

        mvc.perform(get("/api/vagas/{id}", vagaId).with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void coordenadorCriaVagaOndePastoralNaoEResponsavelDaForbidden_ondeJaEDaCreated() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(53));

        String corpo1 = json(new VagaRequestDTO(celebracao.getId(), cenario.funcaoComunicacaoPascom.getId(), 2, null, null));
        mvc.perform(post("/api/vagas").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo1))
                .andExpect(status().isForbidden());

        // padre "responsabiliza" a Pascom por este evento (vaga sem quantidade)
        String corpoResponsabiliza = json(new VagaRequestDTO(celebracao.getId(), cenario.funcaoComunicacaoPascom.getId(), null, null, null));
        mvc.perform(post("/api/vagas").with(user(cenario.principal(cenario.padreA))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoResponsabiliza))
                .andExpect(status().isCreated());

        // agora a Pascom já é responsável: coordenador cria vaga adicional (outra função dela) sem problema
        Funcao outraFuncaoPascom = criarFuncaoDireta(cenario.pascom, "Decoração");
        String corpo2 = json(new VagaRequestDTO(celebracao.getId(), outraFuncaoPascom.getId(), 2, null, null));
        mvc.perform(post("/api/vagas").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo2))
                .andExpect(status().isCreated());
    }

    @Test
    void aplicarFuturasSoAtingeCelebracoesFuturasSemAquelaFuncao() throws Exception {
        Funcao fotografia = criarFuncaoDireta(cenario.pascom, "Fotografia");
        criarModelo(fotografia, TipoCelebracaoModelo.MISSA_DOMINICAL, 2);

        Celebracao passada = criarCelebracaoDireta(LocalDate.now().minusDays(5));
        Celebracao futuraSemVaga = criarCelebracaoDireta(LocalDate.now().plusDays(54));
        Celebracao futuraComVaga = criarCelebracaoDireta(LocalDate.now().plusDays(55));
        Vaga vagaExistente = new Vaga();
        vagaExistente.setCelebracao(futuraComVaga);
        vagaExistente.setFuncao(fotografia);
        vagaExistente.setQuantidade(9);
        vagaExistente.setParoquiaId(cenario.paroquiaA.getId());
        vagaRepository.save(vagaExistente);

        mvc.perform(post("/api/pastorais/{id}/modelos-vaga/aplicar-futuras", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagasCriadas").value(1));

        assertThat(vagaRepository.findByCelebracaoIdAndActiveTrue(passada.getId())).isEmpty();
        assertThat(vagaRepository.findByCelebracaoIdAndActiveTrue(futuraSemVaga.getId())).hasSize(1);
        assertThat(vagaRepository.findByCelebracaoIdAndActiveTrue(futuraComVaga.getId())).hasSize(1)
                .allSatisfy(v -> assertThat(v.getQuantidade()).isEqualTo(9));
    }

    @Test
    void modeloComFuncaoDeOutraPastoralDaErro() throws Exception {
        String corpo = json(new ModeloVagaRequestDTO(cenario.funcaoBarracaEcc.getId(), TipoCelebracaoModelo.TODOS, 2));
        mvc.perform(post("/api/pastorais/{id}/modelos-vaga", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());
    }
}
