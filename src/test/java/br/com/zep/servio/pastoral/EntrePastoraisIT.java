package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.AlocacaoRequestDTO;
import br.com.zep.servio.model.dto.LancamentoFinanceiroRequestDTO;
import br.com.zep.servio.model.dto.ModeloVagaRequestDTO;
import br.com.zep.servio.model.dto.PastoralConfigRequestDTO;
import br.com.zep.servio.model.dto.ReuniaoRequestDTO;
import br.com.zep.servio.model.dto.UsuarioPastoralRequestDTO;
import br.com.zep.servio.model.dto.VagaRequestDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracaoModelo;
import br.com.zep.servio.model.enumerated.TipoLancamento;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parte 5.3: o coordenador da Pascom contra tudo que é do ECC (zero relação com ela) dentro da
 * mesma paróquia — cada ação em recurso alheio dá 403 ou 404 conforme a regra "pastoral fora do
 * alcance" da Parte 4/5.2, e recursos do ECC não aparecem nas listagens do coordenador da Pascom.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class EntrePastoraisIT {

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
    AlocacaoRepository alocacaoRepository;
    @Autowired
    AlteracaoPendenteRepository alteracaoPendenteRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    Comunidade comunidade;
    Celebracao celebracaoPascom;
    Celebracao celebracaoEcc;
    Vaga vagaPascom;
    Vaga vagaEcc;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        comunidade = new Comunidade();
        comunidade.setNome("Comunidade Central");
        comunidade.setParoquiaId(cenario.paroquiaA.getId());
        comunidade = comunidadeRepository.save(comunidade);

        celebracaoPascom = criarCelebracaoDireta(LocalDate.now().plusDays(20));
        vagaPascom = criarVagaDireta(celebracaoPascom, cenario.funcaoComunicacaoPascom, 5);

        celebracaoEcc = criarCelebracaoDireta(LocalDate.now().plusDays(21));
        vagaEcc = criarVagaDireta(celebracaoEcc, cenario.funcaoBarracaEcc, 5);
    }

    private String json(Object dto) {
        return objectMapper.writeValueAsString(dto);
    }

    private Celebracao criarCelebracaoDireta(LocalDate data) {
        Celebracao celebracao = new Celebracao();
        celebracao.setComunidade(comunidade);
        celebracao.setData(data);
        celebracao.setHora(LocalTime.of(10, 0));
        celebracao.setParoquiaId(cenario.paroquiaA.getId());
        return celebracaoRepository.save(celebracao);
    }

    private Vaga criarVagaDireta(Celebracao celebracao, br.com.zep.servio.model.Funcao funcao, Integer quantidade) {
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        vaga.setFuncao(funcao);
        vaga.setQuantidade(quantidade);
        vaga.setParoquiaId(cenario.paroquiaA.getId());
        return vagaRepository.save(vaga);
    }

    private Alocacao criarAlocacaoDireta(Vaga vaga) {
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        alocacao.setUsuario(cenario.membroEcc);
        alocacao.setStatus(StatusConvite.ACEITA);
        alocacao.setParoquiaId(cenario.paroquiaA.getId());
        return alocacaoRepository.save(alocacao);
    }

    private AlteracaoPendente criarPendenteDireta(Alocacao alocacao) {
        AlteracaoPendente pendente = new AlteracaoPendente();
        pendente.setAlocacao(alocacao);
        pendente.setPastoral(cenario.ecc);
        pendente.setAutor(cenario.coordenadorEcc);
        pendente.setVagaAnterior(alocacao.getVaga());
        pendente.setUsuarioAnterior(alocacao.getUsuario());
        pendente.setVagaNova(alocacao.getVaga());
        pendente.setUsuarioNovo(alocacao.getUsuario());
        pendente.setStatus(StatusAlteracaoPendente.PENDENTE);
        pendente.setParoquiaId(cenario.paroquiaA.getId());
        return alteracaoPendenteRepository.save(pendente);
    }

    @Test
    void criarVagaNoEcc() throws Exception {
        String corpo = json(new VagaRequestDTO(celebracaoEcc.getId(), cenario.funcaoBarracaEcc.getId(), 3, null, null));
        mvc.perform(post("/api/vagas").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void editarVagaDoEcc() throws Exception {
        String corpo = json(new VagaRequestDTO(celebracaoEcc.getId(), cenario.funcaoBarracaEcc.getId(), 7, null, null));
        mvc.perform(put("/api/vagas/{id}", vagaEcc.getId()).with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirVagaDoEcc() throws Exception {
        mvc.perform(delete("/api/vagas/{id}", vagaEcc.getId()).with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarAlocacaoNoEcc() throws Exception {
        String corpo = json(new AlocacaoRequestDTO(vagaEcc.getId(), cenario.membroEcc.getId()));
        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void editarAlocacaoDoEcc() throws Exception {
        Alocacao alocacao = criarAlocacaoDireta(vagaEcc);
        String corpo = json(new AlocacaoRequestDTO(vagaEcc.getId(), cenario.membroEcc.getId()));
        mvc.perform(put("/api/alocacoes/{id}", alocacao.getId()).with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void excluirAlocacaoDoEcc() throws Exception {
        Alocacao alocacao = criarAlocacaoDireta(vagaEcc);
        mvc.perform(delete("/api/alocacoes/{id}", alocacao.getId()).with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void moverAlocacaoDaPascomParaVagaDoEcc() throws Exception {
        Alocacao alocacaoPascom = criarAlocacaoDireta(vagaPascom);
        String corpo = json(new AlocacaoRequestDTO(vagaEcc.getId(), cenario.membroEcc.getId()));
        mvc.perform(put("/api/alocacoes/{id}", alocacaoPascom.getId()).with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void trocarPastoralDeParticipacaoDaPropriaPastoralParaOutra() throws Exception {
        var participacao = usuarioPastoralRepository
                .findByUsuarioIdAndPastoralIdAndActiveTrue(cenario.membro1Pascom.getId(), cenario.pascom.getId())
                .orElseThrow();
        String corpo = json(new UsuarioPastoralRequestDTO(cenario.membro1Pascom.getId(), cenario.ecc.getId(), PapelPastoral.MEMBRO));
        mvc.perform(put("/api/usuarios-pastorais/{id}", participacao.getId()).with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void confirmarPendenciaDoEcc() throws Exception {
        Alocacao alocacao = criarAlocacaoDireta(vagaEcc);
        AlteracaoPendente pendente = criarPendenteDireta(alocacao);
        mvc.perform(post("/api/alteracoes-pendentes/{id}/confirmar", pendente.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void desfazerPendenciaDoEcc() throws Exception {
        Alocacao alocacao = criarAlocacaoDireta(vagaEcc);
        AlteracaoPendente pendente = criarPendenteDireta(alocacao);
        mvc.perform(post("/api/alteracoes-pendentes/{id}/desfazer", pendente.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void reuniaoDoEcc() throws Exception {
        mvc.perform(get("/api/pastorais/{id}/reunioes", cenario.ecc.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isNotFound());

        String corpo = json(new ReuniaoRequestDTO("Reunião do ECC", LocalDateTime.now().plusDays(3), "Salão"));
        mvc.perform(post("/api/pastorais/{id}/reunioes", cenario.ecc.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void financeiroDoEcc() throws Exception {
        mvc.perform(get("/api/pastorais/{id}/financeiro", cenario.ecc.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isNotFound());

        String corpo = json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, BigDecimal.TEN, "Doação", LocalDate.now()));
        mvc.perform(post("/api/pastorais/{id}/financeiro", cenario.ecc.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void configDoEcc() throws Exception {
        String corpo = json(new PastoralConfigRequestDTO(true, Map.of("horas", 60)));
        mvc.perform(put("/api/pastorais/{id}/config/INTERVALO_MINIMO", cenario.ecc.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void modeloDeVagaDoEcc() throws Exception {
        String corpo = json(new ModeloVagaRequestDTO(cenario.funcaoBarracaEcc.getId(), TipoCelebracaoModelo.TODOS, 2));
        mvc.perform(post("/api/pastorais/{id}/modelos-vaga", cenario.ecc.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void recursosDoEccNaoAparecemNasListagensDoCoordenadorDaPascom() throws Exception {
        Alocacao alocacaoEcc = criarAlocacaoDireta(vagaEcc);
        criarPendenteDireta(alocacaoEcc);

        assertThat(vagaRepository.findByParoquiaIdAndFuncaoPastoralIdInAndActiveTrue(
                        cenario.paroquiaA.getId(), java.util.List.of(cenario.pascom.getId()),
                        org.springframework.data.domain.Pageable.unpaged()).stream()
                .map(Vaga::getId))
                .contains(vagaPascom.getId())
                .doesNotContain(vagaEcc.getId());

        mvc.perform(get("/api/vagas").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("\"id\":" + vagaPascom.getId());
                    assertThat(body).doesNotContain("\"id\":" + vagaEcc.getId());
                });

        mvc.perform(get("/api/alteracoes-pendentes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).doesNotContain("\"pastoralId\":" + cenario.ecc.getId());
                });

        mvc.perform(get("/api/celebracoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("\"id\":" + celebracaoPascom.getId());
                    assertThat(body).doesNotContain("\"id\":" + celebracaoEcc.getId());
                });
    }
}
