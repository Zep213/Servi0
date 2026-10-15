package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.CelebracaoRequestDTO;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoData;
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
import br.com.zep.servio.service.notification.Notificador;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 4, cartão 4: o prazo do convite nunca passa da hora da celebração. A invariante
 * {@code dataLimiteResposta <= início da celebração} vale em todos os caminhos testados aqui.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class PrazoConviteIT {

    @Autowired
    MockMvc mvc;
    /** O mesmo relógio (e fuso, servio.fuso) do serviço: o runner roda em UTC e o app em America/Fortaleza. */
    @Autowired
    Clock clock;
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
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Comunidade comunidade;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        comunidade = escala.comunidade(cenario.paroquiaA);
    }

    private LocalDateTime agoraMais(Duration duracao) {
        return LocalDateTime.now(clock).plus(duracao).withSecond(0).withNano(0);
    }

    private Celebracao celebracaoEm(LocalDateTime inicio) {
        Celebracao celebracao = escala.celebracao(cenario.paroquiaA, comunidade, inicio.toLocalDate(),
                TipoCelebracao.MISSA_DOMINICAL);
        celebracao.setHora(inicio.toLocalTime());
        return celebracaoRepository.save(celebracao);
    }

    private Vaga vagaDe(Celebracao celebracao) {
        return escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 2);
    }

    private ResultActions escalar(Vaga vaga, Usuario convidado) throws Exception {
        return mvc.perform(post("/api/vagas/{id}/escalar", vaga.getId())
                .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                .contentType("application/json")
                .content("{\"usuarioId\":" + convidado.getId() + ",\"forcar\":true}"));
    }

    private LocalDateTime prazoDa(ResultActions resposta) throws Exception {
        MvcResult resultado = resposta.andExpect(status().isCreated()).andReturn();
        String texto = json.readTree(resultado.getResponse().getContentAsString()).get("dataLimiteResposta").asString();
        return LocalDateTime.parse(texto);
    }

    private ResultActions editarCelebracao(Celebracao celebracao, LocalDateTime novoInicio) throws Exception {
        String corpo = json.writeValueAsString(new CelebracaoRequestDTO(comunidade.getId(), novoInicio.toLocalDate(),
                novoInicio.toLocalTime(), TipoData.NORMAL, TipoCelebracao.MISSA_DOMINICAL, null));
        MockHttpServletRequestBuilder requisicao = put("/api/celebracoes/{id}", celebracao.getId())
                .with(user(cenario.principal(cenario.padreA))).with(csrf())
                .contentType("application/json").content(corpo);
        return mvc.perform(requisicao);
    }

    @Test
    void celebracaoDistanteMantemOPrazoDaPastoral() throws Exception {
        Celebracao celebracao = celebracaoEm(agoraMais(Duration.ofDays(3)));

        LocalDateTime prazo = prazoDa(escalar(vagaDe(celebracao), cenario.membro2Pascom));

        assertThat(prazo).isBetween(LocalDateTime.now(clock).plusHours(24).minusMinutes(2), LocalDateTime.now(clock).plusHours(24).plusMinutes(2));
        assertThat(prazo).isBeforeOrEqualTo(celebracao.getData().atTime(celebracao.getHora()));
    }

    @Test
    void celebracaoDaquiATresHorasLimitaOPrazoAHoraDaMissa() throws Exception {
        LocalDateTime inicio = agoraMais(Duration.ofHours(3));
        Celebracao celebracao = celebracaoEm(inicio);

        LocalDateTime prazo = prazoDa(escalar(vagaDe(celebracao), cenario.membro2Pascom));

        assertThat(prazo).isEqualTo(inicio);
    }

    @Test
    void celebracaoQueJaComecouDa422ENadaEhCriado() throws Exception {
        Celebracao celebracao = celebracaoEm(agoraMais(Duration.ofHours(-1)));
        Vaga vaga = vagaDe(celebracao);

        escalar(vaga, cenario.membro2Pascom)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Esta celebração já começou"));

        assertThat(alocacaoRepository.findByVagaIdAndActiveTrue(vaga.getId())).isEmpty();
    }

    @Test
    void mudarAHoraParaMaisCedoEncurtaOConvitePendenteEMaisTardeNaoEstende() throws Exception {
        Celebracao celebracao = celebracaoEm(agoraMais(Duration.ofDays(3)));
        Vaga vaga = vagaDe(celebracao);
        long alocacaoId = json.readTree(escalar(vaga, cenario.membro2Pascom).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        LocalDateTime prazoInicial = alocacaoRepository.findById(alocacaoId).orElseThrow().getDataLimiteResposta();

        LocalDateTime maisCedo = agoraMais(Duration.ofHours(2));
        editarCelebracao(celebracao, maisCedo).andExpect(status().isOk());
        Alocacao encurtada = alocacaoRepository.findById(alocacaoId).orElseThrow();
        assertThat(encurtada.getDataLimiteResposta()).isEqualTo(maisCedo).isBefore(prazoInicial);
        assertThat(encurtada.getTokenHash()).isNotNull();

        editarCelebracao(celebracao, agoraMais(Duration.ofDays(5))).andExpect(status().isOk());
        assertThat(alocacaoRepository.findById(alocacaoId).orElseThrow().getDataLimiteResposta()).isEqualTo(maisCedo);
    }

    @Test
    void editarSoOTituloNaoMexeNoPrazo() throws Exception {
        LocalDateTime inicio = agoraMais(Duration.ofDays(3));
        Celebracao celebracao = celebracaoEm(inicio);
        long alocacaoId = json.readTree(escalar(vagaDe(celebracao), cenario.membro2Pascom).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        LocalDateTime antes = alocacaoRepository.findById(alocacaoId).orElseThrow().getDataLimiteResposta();

        String corpo = json.writeValueAsString(new CelebracaoRequestDTO(comunidade.getId(), inicio.toLocalDate(),
                inicio.toLocalTime(), TipoData.NORMAL, TipoCelebracao.MISSA_DOMINICAL, "Missa de Natal"));
        mvc.perform(put("/api/celebracoes/{id}", celebracao.getId())
                        .with(user(cenario.principal(cenario.padreA))).with(csrf())
                        .contentType("application/json").content(corpo))
                .andExpect(status().isOk());

        assertThat(alocacaoRepository.findById(alocacaoId).orElseThrow().getDataLimiteResposta()).isEqualTo(antes);
    }
}
