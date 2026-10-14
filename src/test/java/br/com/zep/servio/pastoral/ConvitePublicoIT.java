package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
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
import br.com.zep.servio.service.escalacao.TokenConvite;
import br.com.zep.servio.service.escalacao.evento.ConviteCriadoEvent;
import br.com.zep.servio.service.escalacao.evento.ConviteRecusadoEvent;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.zep.servio.pastoral.CenarioEscalacao.domingo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Convite pelo link do e-mail (Etapa 6, Parte 3). Sem {@code @Transactional}: o prazo vencido
 * precisa comitar a expiração de verdade. Cada teste usa um IP próprio, para o limite de 30
 * por minuto de um teste não afetar os outros.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@RecordApplicationEvents
class ConvitePublicoIT {

    private static final AtomicInteger IPS = new AtomicInteger(1);

    @Autowired
    MockMvc mvc;
    @Autowired
    Clock clock;
    @Autowired
    tools.jackson.databind.ObjectMapper json;
    @Autowired
    ApplicationEvents eventos;
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
    @Autowired
    TokenConvite tokenConvite;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Vaga vaga;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        Comunidade comunidade = escala.comunidade(cenario.paroquiaA);
        Celebracao celebracao = escala.celebracao(cenario.paroquiaA, comunidade, domingo(5), TipoCelebracao.MISSA_DOMINICAL);
        vaga = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 5);
    }

    /** Escala pelo caminho real (POST /api/alocacoes) e devolve a alocação e o token puro do evento. */
    private record Convite(Long id, String token) {}

    private Convite convidar() throws Exception {
        return convidar(cenario.membro2Pascom);
    }

    private Convite convidar(br.com.zep.servio.model.Usuario usuario) throws Exception {
        var resposta = mvc.perform(post("/api/alocacoes").contentType("application/json")
                        .content("{\"vagaId\":" + vaga.getId() + ",\"usuarioId\":" + usuario.getId() + "}")
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = json.readTree(resposta).get("id").asLong();
        String token = eventos.stream(ConviteCriadoEvent.class)
                .filter(e -> e.alocacaoId().equals(id))
                .findFirst().orElseThrow().token();
        return new Convite(id, token);
    }

    /** Cada teste com um IP próprio: o limite por IP fica isolado entre os testes. */
    private static String ipUnico() {
        int n = IPS.getAndIncrement();
        return "10.0." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions detalhes(String token, String ip) throws Exception {
        return mvc.perform(post("/api/confirmacoes/detalhes").contentType("application/json")
                .content("{\"token\":\"" + token + "\"}")
                .with(csrf())
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                }));
    }

    private ResultActions responder(String token, boolean aceitar, String justificativa, String ip) throws Exception {
        String corpo = "{\"token\":\"" + token + "\",\"aceitar\":" + aceitar
                + (justificativa == null ? "" : ",\"justificativa\":\"" + justificativa + "\"") + "}";
        return mvc.perform(post("/api/confirmacoes/responder").contentType("application/json")
                .content(corpo)
                .with(csrf())
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                }));
    }

    @Test
    void detalhesMostramOConviteENaoAlteramNada() throws Exception {
        Convite convite = convidar();
        Alocacao antes = alocacaoRepository.findById(convite.id()).orElseThrow();

        detalhes(convite.token(), ipUnico())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primeiroNome").value("membro2.pascom"))
                .andExpect(jsonPath("$.funcao").value("Comunicação"))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.prazo").isNotEmpty());

        Alocacao depois = alocacaoRepository.findById(convite.id()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusConvite.PENDENTE);
        assertThat(depois.getTokenHash()).isEqualTo(antes.getTokenHash());
        assertThat(depois.getDataLimiteResposta()).isEqualTo(antes.getDataLimiteResposta());
    }

    @Test
    void confirmarPresencaGravaAceitaEInvalidaOLink() throws Exception {
        Convite convite = convidar();
        String ip = ipUnico();

        responder(convite.token(), true, null, ip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACEITA"));

        Alocacao relida = alocacaoRepository.findById(convite.id()).orElseThrow();
        assertThat(relida.getStatus()).isEqualTo(StatusConvite.ACEITA);
        assertThat(relida.getRespondidoEm()).isNotNull();
        assertThat(relida.getTokenHash()).isNull();
        assertThat(relida.getJustificativa()).isNull();
    }

    @Test
    void recusarGravaJustificativaPublicaEvento() throws Exception {
        Convite convite = convidar();

        responder(convite.token(), false, "Vou viajar nesse dia", ipUnico())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"));

        Alocacao relida = alocacaoRepository.findById(convite.id()).orElseThrow();
        assertThat(relida.getStatus()).isEqualTo(StatusConvite.RECUSADA);
        assertThat(relida.getJustificativa()).isEqualTo("Vou viajar nesse dia");
        assertThat(eventos.stream(ConviteRecusadoEvent.class).filter(e -> e.alocacaoId().equals(convite.id()))).hasSize(1);
    }

    @Test
    void tokenInvalidoReusadoEOuDeAlocacaoInativaDaMesmaNotFound() throws Exception {
        Convite convite = convidar();
        String ip = ipUnico();

        detalhes("token-que-nao-existe-1234567890abcdefghijklmnop", ip)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Convite não encontrado ou já respondido"));

        responder(convite.token(), true, null, ip).andExpect(status().isOk());
        responder(convite.token(), true, null, ip)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Convite não encontrado ou já respondido"));

        Convite inativo = convidar(cenario.secretario1Pascom);
        Alocacao alocacao = alocacaoRepository.findById(inativo.id()).orElseThrow();
        alocacao.setActive(false);
        alocacaoRepository.save(alocacao);
        detalhes(inativo.token(), ip)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Convite não encontrado ou já respondido"));
    }

    @Test
    void prazoVencidoDa410EGravaExpiradaNoBanco() throws Exception {
        Convite convite = convidar();
        Alocacao alocacao = alocacaoRepository.findById(convite.id()).orElseThrow();
        alocacao.setDataLimiteResposta(LocalDateTime.now(clock).minusMinutes(1));
        alocacaoRepository.save(alocacao);

        responder(convite.token(), true, null, ipUnico()).andExpect(status().isGone());

        Alocacao relida = alocacaoRepository.findById(convite.id()).orElseThrow();
        assertThat(relida.getStatus()).isEqualTo(StatusConvite.EXPIRADA);
    }

    @Test
    void tokenPuroNuncaFicaNoBanco() throws Exception {
        Convite convite = convidar();

        Alocacao gravada = alocacaoRepository.findById(convite.id()).orElseThrow();
        assertThat(gravada.getTokenHash()).isNotEqualTo(convite.token()).hasSize(64);
        assertThat(gravada.getTokenHash()).isEqualTo(tokenConvite.hash(convite.token()));
    }

    @Test
    void trintaUmaChamadasNoMinutoDaErro429() throws Exception {
        String ip = ipUnico();
        for (int i = 0; i < 30; i++) {
            detalhes("token-invalido-" + i + "-abcdefghijklmnopqrstuvwxyz", ip).andExpect(status().isNotFound());
        }
        detalhes("token-invalido-31-abcdefghijklmnopqrstuvwxyz", ip).andExpect(status().isTooManyRequests());
    }
}
