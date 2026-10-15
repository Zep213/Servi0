package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.PapelPastoral;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lista de membros com filtros e status de ativação, e detalhe do membro (Sprint 3, Cartão 5). A situação da conta e o
 * e-mail só vão para a coordenação; o detalhe nunca traz telefone, senha nem token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class MembrosStatusIT {

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

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Usuario pendente;
    Usuario protegida;
    Usuario jose;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);

        pendente = cenario.naPascom("pendente.pascom", PapelPastoral.MEMBRO);
        pendente.setSenha(null);
        usuarioRepository.save(pendente);

        protegida = cenario.naPascom("protegida.pascom", PapelPastoral.MEMBRO);
        protegida.setSenha(null);
        protegida.setAtivadoEm(LocalDateTime.now());
        usuarioRepository.save(protegida);

        jose = cenario.naPascom("jose.pascom", PapelPastoral.TECNICO);
        jose.setNome("José Ação");
        usuarioRepository.save(jose);
    }

    private ResultActions listar(Usuario quem, String consulta) throws Exception {
        return mvc.perform(get("/api/pastorais/{p}/membros" + consulta, cenario.pascom.getId())
                .with(user(cenario.principal(quem))));
    }

    private List<Long> idsDaLista(ResultActions resposta) throws Exception {
        MvcResult resultado = resposta.andExpect(status().isOk()).andReturn();
        List<Long> ids = new ArrayList<>();
        for (JsonNode item : json.readTree(resultado.getResponse().getContentAsString())) {
            ids.add(item.get("usuario").get("id").asLong());
        }
        return ids;
    }

    @Test
    void statusDeAtivacaoVemParaACoordenacao() throws Exception {
        MvcResult resultado = listar(cenario.coordenadorPascom, "").andExpect(status().isOk()).andReturn();
        for (JsonNode item : json.readTree(resultado.getResponse().getContentAsString())) {
            long id = item.get("usuario").get("id").asLong();
            if (id == pendente.getId()) {
                assertThat(item.get("statusAtivacao").asText()).isEqualTo("PENDENTE");
            } else if (id == protegida.getId()) {
                assertThat(item.get("statusAtivacao").asText()).isEqualTo("PROTEGIDA");
            } else if (id == cenario.membro1Pascom.getId()) {
                assertThat(item.get("statusAtivacao").asText()).isEqualTo("ATIVA");
                assertThat(item.get("ativada").asBoolean()).isTrue();
            }
        }
    }

    @Test
    void situacaoDaContaEEmailNaoVaoParaQuemNaoCoordena() throws Exception {
        listar(cenario.membro2Pascom, "").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.statusAtivacao != null)]").isEmpty())
                .andExpect(jsonPath("$[?(@.ativada != null)]").isEmpty())
                .andExpect(jsonPath("$[?(@.email != null)]").isEmpty());
        // filtrar por status também revelaria a situação da conta dos outros
        listar(cenario.membro2Pascom, "?status=PENDENTE").andExpect(status().isForbidden());
        assertThat(idsDaLista(listar(cenario.coordenadorPascom, "?status=PENDENTE"))).containsExactly(pendente.getId());
    }

    @Test
    void filtrosPorNomePapelHabilidadeECombinados() throws Exception {
        // sem diferenciar maiúsculas nem acentos
        assertThat(idsDaLista(listar(cenario.membro2Pascom, "?nome=JOSE"))).containsExactly(jose.getId());
        assertThat(idsDaLista(listar(cenario.membro2Pascom, "?nome=ação"))).containsExactly(jose.getId());
        assertThat(idsDaLista(listar(cenario.membro2Pascom, "?papel=VICE"))).containsExactly(cenario.vicePascom.getId());

        cenario.habilitar(usuarioFuncaoRepository, cenario.membro1Pascom, cenario.funcaoComunicacaoPascom);
        cenario.habilitar(usuarioFuncaoRepository, cenario.vicePascom, cenario.funcaoComunicacaoPascom);
        String habilidade = "habilidade=" + cenario.funcaoComunicacaoPascom.getId();
        assertThat(idsDaLista(listar(cenario.membro2Pascom, "?" + habilidade)))
                .containsExactlyInAnyOrder(cenario.membro1Pascom.getId(), cenario.vicePascom.getId());
        assertThat(idsDaLista(listar(cenario.membro2Pascom, "?" + habilidade + "&papel=MEMBRO")))
                .containsExactly(cenario.membro1Pascom.getId());
        // sem filtro, devolve todos como antes
        assertThat(idsDaLista(listar(cenario.membro2Pascom, ""))).contains(cenario.coordenadorPascom.getId(),
                cenario.membro1Pascom.getId(), pendente.getId(), protegida.getId(), jose.getId());
    }

    @Test
    void parametroInvalidoDa400() throws Exception {
        listar(cenario.coordenadorPascom, "?papel=XYZ").andExpect(status().isBadRequest());
        listar(cenario.coordenadorPascom, "?status=XYZ").andExpect(status().isBadRequest());
        listar(cenario.coordenadorPascom, "?habilidade=abc").andExpect(status().isBadRequest());
    }

    @Test
    void detalheTemHabilidadesUltimoServicoEConvitesPendentesSoDestaPastoral() throws Exception {
        cenario.habilitar(usuarioFuncaoRepository, cenario.membro1Pascom, cenario.funcaoComunicacaoPascom);
        var comunidade = escala.comunidade(cenario.paroquiaA);
        LocalDate passado = LocalDate.now().minusDays(10);
        Celebracao anterior = escala.celebracao(cenario.paroquiaA, comunidade, passado, TipoCelebracao.MISSA_DOMINICAL);
        Vaga vagaAnterior = escala.vaga(cenario.paroquiaA, anterior, cenario.funcaoComunicacaoPascom, 1);
        escala.alocacao(cenario.paroquiaA, vagaAnterior, cenario.membro1Pascom, StatusConvite.ACEITA);
        Celebracao futura = escala.celebracao(cenario.paroquiaA, comunidade, LocalDate.now().plusDays(20),
                TipoCelebracao.MISSA_DOMINICAL);
        escala.alocacao(cenario.paroquiaA, escala.vaga(cenario.paroquiaA, futura, cenario.funcaoComunicacaoPascom, 1),
                cenario.membro1Pascom, StatusConvite.PENDENTE);
        // convite pendente em OUTRA pastoral não conta
        escala.alocacao(cenario.paroquiaA, escala.vaga(cenario.paroquiaA, futura, cenario.funcaoBarracaEcc, 1),
                cenario.membro1Pascom, StatusConvite.PENDENTE);

        MvcResult resultado = mvc.perform(get("/api/pastorais/{p}/membros/{u}", cenario.pascom.getId(),
                        cenario.membro1Pascom.getId()).with(user(cenario.principal(cenario.coordenadorPascom))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.habilidades.length()").value(1))
                .andExpect(jsonPath("$.habilidades[0].nome").value("Comunicação"))
                .andExpect(jsonPath("$.ultimoServico").value(passado.toString()))
                .andExpect(jsonPath("$.convitesPendentes").value(1))
                .andExpect(jsonPath("$.statusAtivacao").value("ATIVA"))
                .andReturn();

        // só estes campos: nada de telefone, senha, hash ou token
        Set<String> campos = new java.util.HashSet<>();
        json.readTree(resultado.getResponse().getContentAsString()).propertyNames().forEach(campos::add);
        assertThat(campos).isSubsetOf("usuario", "papel", "habilidades", "ultimoServico", "convitesPendentes",
                "email", "statusAtivacao", "ativadoEm");
    }

    @Test
    void detalheParaQuemNaoCoordenaNaoTemContatoNemSituacaoDaConta() throws Exception {
        mvc.perform(get("/api/pastorais/{p}/membros/{u}", cenario.pascom.getId(), pendente.getId())
                        .with(user(cenario.principal(cenario.membro2Pascom))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.id").value(pendente.getId()))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.statusAtivacao").doesNotExist())
                .andExpect(jsonPath("$.ativadoEm").doesNotExist());
    }

    @Test
    void naoEncontradoDa404() throws Exception {
        // pessoa de outra pastoral, id inexistente e pastoral fora do alcance
        mvc.perform(get("/api/pastorais/{p}/membros/{u}", cenario.pascom.getId(), cenario.membroEcc.getId())
                .with(user(cenario.principal(cenario.coordenadorPascom)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/pastorais/{p}/membros/{u}", cenario.pascom.getId(), 999_999_999L)
                .with(user(cenario.principal(cenario.coordenadorPascom)))).andExpect(status().isNotFound());
        mvc.perform(get("/api/pastorais/{p}/membros/{u}", cenario.pascom.getId(), cenario.membro1Pascom.getId())
                .with(user(cenario.principal(cenario.coordenadorEcc)))).andExpect(status().isNotFound());
    }
}
