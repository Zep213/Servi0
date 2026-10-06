package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Indisponibilidade;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.IndisponibilidadeRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.escalacao.TokenConvite;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static br.com.zep.servio.pastoral.CenarioEscalacao.domingo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Rotas de leitura para as telas (Etapa 7, Parte 1): conteúdo, permissão (membro, outra pastoral,
 * outra paróquia) e número de consultas que não cresce com a quantidade de linhas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class LeituraIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManagerFactory emf;
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
    IndisponibilidadeRepository indisponibilidadeRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    TokenConvite tokenConvite;

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
        celebracao = escala.celebracao(cenario.paroquiaA, comunidade, domingo(6), TipoCelebracao.MISSA_DOMINICAL);
        celebracao.setTitulo("Missa " + UUID.randomUUID());
        celebracaoRepository.save(celebracao);
        vaga = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 5);
        vaga.setHorarioChegada(java.time.LocalTime.of(18, 30));
        vaga.setObservacao("Levar o microfone");
        vagaRepository.save(vaga);
    }

    private Alocacao alocar(Vaga v, Usuario u, StatusConvite status) {
        Alocacao a = escala.alocacao(cenario.paroquiaA, v, u, status);
        a.setDataLimiteResposta(LocalDateTime.now().plusDays(1));
        a.setTokenHash(tokenConvite.hash("t-" + UUID.randomUUID()));
        return alocacaoRepository.save(a);
    }

    private ResultActions como(Usuario quem, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b) throws Exception {
        return mvc.perform(b.with(user(cenario.principal(quem))));
    }

    /** Consultas SQL (preparadas) que a requisição disparou, medidas pelas estatísticas do Hibernate. */
    private long consultasDe(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b, Usuario quem)
            throws Exception {
        SessionFactory sf = emf.unwrap(SessionFactory.class);
        sf.getStatistics().setStatisticsEnabled(true);
        sf.getStatistics().clear();
        como(quem, b).andExpect(status().isOk());
        return sf.getStatistics().getPrepareStatementCount();
    }

    @Test
    void minhasEscalasTrazNomesEMinhasSemSubstituidas() throws Exception {
        alocar(vaga, cenario.membro1Pascom, StatusConvite.ACEITA);
        alocar(vaga, cenario.membro2Pascom, StatusConvite.SUBSTITUIDA);

        como(cenario.membro1Pascom, get("/api/me/escalas").param("de", LocalDate.now().toString())
                .param("ate", LocalDate.now().plusDays(60).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("ACEITA"))
                .andExpect(jsonPath("$[0].horarioChegada").value("18:30:00"))
                .andExpect(jsonPath("$[0].observacao").value("Levar o microfone"))
                .andExpect(jsonPath("$[0].funcao.nome").value("Comunicação"))
                .andExpect(jsonPath("$[0].pastoral.nome").value("Pascom"))
                .andExpect(jsonPath("$[0].celebracao.titulo").isNotEmpty());

        como(cenario.membro2Pascom, get("/api/me/escalas").param("de", LocalDate.now().toString())
                .param("ate", LocalDate.now().plusDays(60).toString()))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void minhasEscalasRecusaPeriodoInvertido() throws Exception {
        como(cenario.membro1Pascom, get("/api/me/escalas").param("de", "2031-05-10").param("ate", "2031-05-01"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void escalaDaCelebracaoMostraAsVagasEQuemEstaEscalado() throws Exception {
        alocar(vaga, cenario.membro1Pascom, StatusConvite.ACEITA);
        alocar(vaga, cenario.membro2Pascom, StatusConvite.PENDENTE);

        como(cenario.coordenadorPascom, get("/api/pastorais/{p}/celebracoes/{c}/escala", cenario.pascom.getId(), celebracao.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vagas[0].funcao.nome").value("Comunicação"))
                .andExpect(jsonPath("$.vagas[0].quantidade").value(5))
                .andExpect(jsonPath("$.vagas[0].alocacoes.length()").value(2))
                .andExpect(jsonPath("$.vagas[0].alocacoes[0].usuario.nome").isNotEmpty());

        como(cenario.membro1Pascom, get("/api/pastorais/{p}/celebracoes/{c}/escala", cenario.pascom.getId(), celebracao.getId()))
                .andExpect(status().isForbidden());
        como(cenario.coordenadorPascom, get("/api/pastorais/{p}/celebracoes/{c}/escala", cenario.ecc.getId(), celebracao.getId()))
                .andExpect(status().isNotFound());
        como(cenario.padreB, get("/api/pastorais/{p}/celebracoes/{c}/escala", cenario.pascom.getId(), celebracao.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void membrosMostramEmailSoParaCoordenacao() throws Exception {
        como(cenario.coordenadorPascom, get("/api/pastorais/{p}/membros", cenario.pascom.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").isNotEmpty());

        como(cenario.membro1Pascom, get("/api/pastorais/{p}/membros", cenario.pascom.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(org.hamcrest.Matchers.nullValue()));

        como(cenario.membro1Pascom, get("/api/pastorais/{p}/membros", cenario.ecc.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void celebracoesFiltramPorPeriodo() throws Exception {
        LocalDate dia = celebracao.getData();
        como(cenario.padreA, get("/api/celebracoes").param("de", dia.toString()).param("ate", dia.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
        como(cenario.padreA, get("/api/celebracoes").param("de", dia.plusDays(1).toString()).param("ate", dia.plusDays(30).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void funcoesFiltramPorPastoral() throws Exception {
        como(cenario.padreA, get("/api/funcoes").param("pastoralId", cenario.ecc.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].nome").value("Barraca"))
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void servidorVeSoAsPropriasIndisponibilidades() throws Exception {
        Indisponibilidade minha = novaIndisponibilidade(cenario.membro1Pascom);
        novaIndisponibilidade(cenario.membro2Pascom);

        como(cenario.membro1Pascom, get("/api/indisponibilidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(minha.getId()));
        como(cenario.padreA, get("/api/indisponibilidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    private Indisponibilidade novaIndisponibilidade(Usuario u) {
        Indisponibilidade i = new Indisponibilidade();
        i.setParoquiaId(cenario.paroquiaA.getId());
        i.setUsuario(u);
        i.setDataInicio(LocalDate.now().plusDays(2));
        i.setDataFim(LocalDate.now().plusDays(3));
        i.setMotivo("Viagem");
        return indisponibilidadeRepository.save(i);
    }

    /** As consultas de /api/me/escalas e da escala da celebração não crescem com o número de alocações. */
    @Test
    void consultasNaoCrescemPorLinha() throws Exception {
        LocalDate de = LocalDate.now();
        LocalDate ate = de.plusDays(60);
        alocar(vaga, cenario.membro1Pascom, StatusConvite.ACEITA);
        alocar(vaga, cenario.membro2Pascom, StatusConvite.ACEITA);
        long escalasPoucas = consultasDe(get("/api/me/escalas").param("de", de.toString()).param("ate", ate.toString()),
                cenario.membro1Pascom);
        long escalaPoucas = consultasDe(get("/api/pastorais/{p}/celebracoes/{c}/escala", cenario.pascom.getId(),
                celebracao.getId()), cenario.coordenadorPascom);

        // 18 alocações a mais para o mesmo membro, cada uma numa vaga própria
        for (int i = 0; i < 18; i++) {
            Vaga extra = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, 5);
            alocar(extra, cenario.membro1Pascom, StatusConvite.ACEITA);
        }
        long escalasMuitas = consultasDe(get("/api/me/escalas").param("de", de.toString()).param("ate", ate.toString()),
                cenario.membro1Pascom);
        long escalaMuitas = consultasDe(get("/api/pastorais/{p}/celebracoes/{c}/escala", cenario.pascom.getId(),
                celebracao.getId()), cenario.coordenadorPascom);

        assertThat(escalasMuitas).isEqualTo(escalasPoucas);
        assertThat(escalaMuitas).isEqualTo(escalaPoucas);
    }
}
