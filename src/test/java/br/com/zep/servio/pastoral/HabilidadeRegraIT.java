package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioFuncao;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioFuncaoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.escalacao.evento.VagaSemElegiveisEvent;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Set;
import java.util.stream.Collectors;

import static br.com.zep.servio.pastoral.CenarioEscalacao.domingo;
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
 * Regra HABILIDADE (Sprint 3, Cartão 2) de ponta a ponta. Esta classe liga a regra por padrão
 * ({@code servio.escalacao.habilidade-ligada-por-padrao=true}, que é o valor de produção); os demais ITs rodam com ela
 * desligada no application-test. Sem {@code @Transactional}: o sorteio publica eventos que só valem com commit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@RecordApplicationEvents
@TestPropertySource(properties = "servio.escalacao.habilidade-ligada-por-padrao=true")
class HabilidadeRegraIT {

    private static final String SEM_HABILIDADE = "Não tem a habilidade desta vaga";

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
    UsuarioFuncaoRepository usuarioFuncaoRepository;
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
    ObjectMapper json;
    @Autowired
    ApplicationEvents eventos;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Comunidade comunidade;
    Funcao som;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        comunidade = escala.comunidade(cenario.paroquiaA);
        som = new Funcao();
        som.setNome("Som");
        som.setPastoral(cenario.pascom);
        som.setParoquiaId(cenario.paroquiaA.getId());
        som = funcaoRepository.save(som);
    }

    private ResultActions como(Usuario quem, MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao.with(user(cenario.principal(quem))).with(csrf()));
    }

    private Vaga vagaDeSom(int semanasAFrente, int quantidade) {
        Celebracao celebracao = escala.celebracao(cenario.paroquiaA, comunidade, domingo(semanasAFrente),
                TipoCelebracao.MISSA_DOMINICAL);
        return escala.vaga(cenario.paroquiaA, celebracao, som, quantidade);
    }

    private ResultActions escalar(Usuario quem, Vaga vaga, Usuario convidado, boolean forcar) throws Exception {
        return como(quem, post("/api/vagas/{id}/escalar", vaga.getId())
                .contentType("application/json")
                .content("{\"usuarioId\":" + convidado.getId() + ",\"forcar\":" + forcar + "}"));
    }

    private ResultActions sortear(Vaga vaga) throws Exception {
        return como(cenario.coordenadorPascom, post("/api/pastorais/{p}/celebracoes/{c}/sortear",
                cenario.pascom.getId(), vaga.getCelebracao().getId()));
    }

    private Set<Long> convidados(Vaga vaga) {
        return alocacaoRepository.findByVagaIdAndActiveTrue(vaga.getId()).stream()
                .filter(a -> StatusConvite.OCUPANTES.contains(a.getStatus()))
                .map(a -> a.getUsuario().getId())
                .collect(Collectors.toSet());
    }

    private JsonNode candidato(Vaga vaga, Usuario usuario) throws Exception {
        MvcResult resultado = como(cenario.coordenadorPascom, get("/api/vagas/{id}/candidatos", vaga.getId()))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode item : json.readTree(resultado.getResponse().getContentAsString())) {
            if (item.get("usuario").get("id").asLong() == usuario.getId()) {
                return item;
            }
        }
        throw new AssertionError("candidato " + usuario.getId() + " não está na lista");
    }

    @Test
    void candidatosMostramQuemNaoTemAHabilidadeComOMotivo() throws Exception {
        Vaga vaga = vagaDeSom(6, 2);
        cenario.habilitar(usuarioFuncaoRepository, cenario.membro1Pascom, som);

        JsonNode comHabilidade = candidato(vaga, cenario.membro1Pascom);
        JsonNode semHabilidade = candidato(vaga, cenario.membro2Pascom);

        assertThat(comHabilidade.get("elegivel").asBoolean()).isTrue();
        assertThat(comHabilidade.get("motivos")).isEmpty();
        assertThat(semHabilidade.get("elegivel").asBoolean()).isFalse();
        assertThat(semHabilidade.get("motivos").get(0).asText()).isEqualTo(SEM_HABILIDADE);
    }

    @Test
    void escalacaoManualBarraQuemNaoTemAHabilidadeESoOCoordenadorForca() throws Exception {
        Vaga vaga = vagaDeSom(6, 5);
        cenario.habilitar(usuarioFuncaoRepository, cenario.membro1Pascom, som);

        escalar(cenario.coordenadorPascom, vaga, cenario.membro1Pascom, false).andExpect(status().isCreated());
        escalar(cenario.coordenadorPascom, vaga, cenario.membro2Pascom, false)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString(SEM_HABILIDADE)));
        // o vice não força impedimento (regra que não muda); o coordenador força
        escalar(cenario.vicePascom, vaga, cenario.membro2Pascom, true).andExpect(status().isForbidden());
        escalar(cenario.coordenadorPascom, vaga, cenario.membro2Pascom, true).andExpect(status().isCreated());
    }

    @Test
    void sorteioSoConvidaQuemTemAHabilidadeEAvisaQuandoFaltaGente() throws Exception {
        Vaga vaga = vagaDeSom(6, 2);
        cenario.habilitar(usuarioFuncaoRepository, cenario.membro1Pascom, som);

        sortear(vaga)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(1))
                .andExpect(jsonPath("$.vagasIncompletas.length()").value(1))
                .andExpect(jsonPath("$.vagasIncompletas[0].faltam").value(1));

        assertThat(convidados(vaga)).containsExactly(cenario.membro1Pascom.getId());
        assertThat(eventos.stream(VagaSemElegiveisEvent.class).filter(e -> e.vagaId().equals(vaga.getId()))).hasSize(1);
    }

    @Test
    void sorteioSemNinguemComAHabilidadeDevolveAVagaIncompleta() throws Exception {
        Vaga vaga = vagaDeSom(6, 2);

        sortear(vaga)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(0))
                .andExpect(jsonPath("$.vagasIncompletas[0].faltam").value(2));

        assertThat(convidados(vaga)).isEmpty();
        assertThat(eventos.stream(VagaSemElegiveisEvent.class).filter(e -> e.vagaId().equals(vaga.getId()))).hasSize(1);
    }

    @Test
    void pastoralQueDesligaAHabilidadeVoltaASortearQualquerMembro() throws Exception {
        Vaga vaga = vagaDeSom(6, 2);

        como(cenario.coordenadorPascom, put("/api/pastorais/{p}/config/HABILIDADE", cenario.pascom.getId())
                .contentType("application/json").content("{\"ativa\":false,\"parametros\":{}}"))
                .andExpect(status().isOk());

        sortear(vaga).andExpect(status().isOk()).andExpect(jsonPath("$.convidados.length()").value(2));
        // desde o cartão 3 o sorteio considera todos os papéis da pastoral: sem a regra, qualquer um dos 7 da Pascom
        // (ninguém aqui tem a habilidade) pode ser escolhido; o que se prova é que a falta da habilidade não barra mais.
        assertThat(convidados(vaga)).hasSize(2).isSubsetOf(
                cenario.coordenadorPascom.getId(), cenario.vicePascom.getId(), cenario.secretario1Pascom.getId(),
                cenario.secretario2Pascom.getId(), cenario.tesoureiroPascom.getId(), cenario.membro1Pascom.getId(),
                cenario.membro2Pascom.getId());
    }

    @Test
    void desmarcarAHabilidadeTiraAPessoaDoSorteioSeguinte() throws Exception {
        Vaga primeira = vagaDeSom(6, 1);
        UsuarioFuncao habilidade = cenario.habilitar(usuarioFuncaoRepository, cenario.membro1Pascom, som);
        sortear(primeira).andExpect(status().isOk()).andExpect(jsonPath("$.convidados.length()").value(1));
        assertThat(convidados(primeira)).containsExactly(cenario.membro1Pascom.getId());

        como(cenario.coordenadorPascom, delete("/api/usuarios-funcoes/{id}", habilidade.getId()))
                .andExpect(status().isNoContent());

        // semanas depois, para não esbarrar nas regras de intervalo e de domingos seguidos
        Vaga segunda = vagaDeSom(14, 1);
        sortear(segunda).andExpect(status().isOk()).andExpect(jsonPath("$.convidados.length()").value(0));
        assertThat(convidados(segunda)).isEmpty();
    }
}
