package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.AuditLogRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioFuncaoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
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
 * Habilidades gerenciadas pela coordenação da pastoral (Sprint 3, Cartão 1): permissão por pastoral, unicidade só
 * entre ativas (marcar, desmarcar e marcar de novo) e a troca do conjunto de habilidades de um membro.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class HabilidadeGestaoIT {

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
    AuditLogRepository auditLogRepository;
    @Autowired
    PasswordEncoder passwordEncoder;

    CenarioParoquia cenario;
    Funcao som;
    Funcao fotografia;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        som = novaFuncao(cenario.pascom, "Som");
        fotografia = novaFuncao(cenario.pascom, "Fotografia");
    }

    private Funcao novaFuncao(Pastoral pastoral, String nome) {
        Funcao funcao = new Funcao();
        funcao.setNome(nome);
        funcao.setPastoral(pastoral);
        funcao.setParoquiaId(pastoral.getParoquiaId());
        return funcaoRepository.save(funcao);
    }

    private ResultActions pedir(Usuario ator, MockHttpServletRequestBuilder requisicao, String corpo) throws Exception {
        MockHttpServletRequestBuilder construtor = requisicao.with(user(cenario.principal(ator))).with(csrf());
        if (corpo != null) {
            construtor = construtor.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mvc.perform(construtor);
    }

    private ResultActions marcar(Usuario ator, Usuario pessoa, Funcao funcao) throws Exception {
        return pedir(ator, post("/api/usuarios-funcoes"),
                "{\"usuarioId\":" + pessoa.getId() + ",\"funcaoId\":" + funcao.getId() + "}");
    }

    private long idDe(ResultActions resposta) throws Exception {
        return ((Number) JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private ResultActions conjunto(Usuario ator, Usuario pessoa, String ids) throws Exception {
        return pedir(ator, put("/api/pastorais/{p}/membros/{u}/habilidades", cenario.pascom.getId(), pessoa.getId()),
                "{\"funcaoIds\":[" + ids + "]}");
    }

    @Test
    void coordenadorMarcaHabilidadeDeMembroDaPropriaPastoralComAuditoria() throws Exception {
        marcar(cenario.coordenadorPascom, cenario.membro1Pascom, som).andExpect(status().isCreated());

        assertThat(usuarioFuncaoRepository.findByUsuarioIdAndActiveTrue(cenario.membro1Pascom.getId()))
                .extracting(h -> h.getFuncao().getId()).contains(som.getId());
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getTipoEvento()).isEqualTo("HABILIDADE_ALTERADA");
            assertThat(log.getDetalhe()).contains("marcada").doesNotContain("@");
        });
    }

    @Test
    void marcarDesmarcarEMarcarDeNovoFunciona() throws Exception {
        long id = idDe(marcar(cenario.coordenadorPascom, cenario.membro1Pascom, som).andExpect(status().isCreated()));

        pedir(cenario.coordenadorPascom, delete("/api/usuarios-funcoes/{id}", id), null).andExpect(status().isNoContent());
        // antes do fix, isto dava 409 "Usuário já possui esta função" (a checagem ignorava a desativação)
        marcar(cenario.coordenadorPascom, cenario.membro1Pascom, som).andExpect(status().isCreated());
    }

    @Test
    void marcarDuasVezesSemDesmarcarDa409() throws Exception {
        marcar(cenario.coordenadorPascom, cenario.membro1Pascom, som).andExpect(status().isCreated());
        marcar(cenario.coordenadorPascom, cenario.membro1Pascom, som).andExpect(status().isConflict());
    }

    @Test
    void permissoesPorPastoral() throws Exception {
        // coordenador de outra pastoral: a pastoral nem aparece para ele
        marcar(cenario.coordenadorEcc, cenario.membro1Pascom, som).andExpect(status().isNotFound());
        // vice e membro da própria pastoral: só o coordenador marca
        marcar(cenario.vicePascom, cenario.membro1Pascom, som).andExpect(status().isForbidden());
        marcar(cenario.membro2Pascom, cenario.membro1Pascom, som).andExpect(status().isForbidden());
        // padre vale como coordenador
        marcar(cenario.padreA, cenario.membro2Pascom, som).andExpect(status().isCreated());
    }

    @Test
    void desmarcarETrocarDeFuncaoTambemExigemCoordenadorDaPastoral() throws Exception {
        long id = idDe(marcar(cenario.coordenadorPascom, cenario.membro1Pascom, som).andExpect(status().isCreated()));

        pedir(cenario.coordenadorEcc, delete("/api/usuarios-funcoes/{id}", id), null).andExpect(status().isNotFound());
        pedir(cenario.vicePascom, delete("/api/usuarios-funcoes/{id}", id), null).andExpect(status().isForbidden());
        pedir(cenario.vicePascom, put("/api/usuarios-funcoes/{id}", id),
                "{\"usuarioId\":" + cenario.membro1Pascom.getId() + ",\"funcaoId\":" + fotografia.getId() + "}")
                .andExpect(status().isForbidden());
        pedir(cenario.coordenadorPascom, put("/api/usuarios-funcoes/{id}", id),
                "{\"usuarioId\":" + cenario.membro1Pascom.getId() + ",\"funcaoId\":" + fotografia.getId() + "}")
                .andExpect(status().isOk());
    }

    @Test
    void pessoaForaDaPastoralDa422EFuncaoDeOutraParoquiaDa404() throws Exception {
        marcar(cenario.coordenadorPascom, cenario.membroEcc, som)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Esta pessoa não faz parte da pastoral desta função"));

        Funcao deOutraParoquia = novaFuncao(cenario.pastoralB, "Função da outra paróquia");
        marcar(cenario.coordenadorPascom, cenario.membro1Pascom, deOutraParoquia).andExpect(status().isNotFound());
    }

    @Test
    void putDeHabilidadesTrocaOConjuntoEGetMostraQuaisEstaoMarcadas() throws Exception {
        marcar(cenario.coordenadorPascom, cenario.membro1Pascom, som).andExpect(status().isCreated());

        conjunto(cenario.coordenadorPascom, cenario.membro1Pascom, fotografia.getId() + "," + cenario.funcaoComunicacaoPascom.getId())
                .andExpect(status().isOk());

        pedir(cenario.coordenadorPascom, get("/api/pastorais/{p}/membros/{u}/habilidades",
                cenario.pascom.getId(), cenario.membro1Pascom.getId()), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.funcaoId==" + som.getId() + ")].marcada").value(false))
                .andExpect(jsonPath("$[?(@.funcaoId==" + fotografia.getId() + ")].marcada").value(true))
                .andExpect(jsonPath("$[?(@.funcaoId==" + cenario.funcaoComunicacaoPascom.getId() + ")].marcada").value(true));
        // quem foi desmarcado vira inativo, não é apagado
        assertThat(usuarioFuncaoRepository.findByUsuarioIdAndActiveTrue(cenario.membro1Pascom.getId()))
                .extracting(h -> h.getFuncao().getId()).doesNotContain(som.getId());
    }

    @Test
    void funcaoInvalidaNaListaDa404ENadaMuda() throws Exception {
        marcar(cenario.coordenadorPascom, cenario.membro1Pascom, som).andExpect(status().isCreated());

        conjunto(cenario.coordenadorPascom, cenario.membro1Pascom,
                fotografia.getId() + "," + cenario.funcaoBarracaEcc.getId()).andExpect(status().isNotFound());

        assertThat(usuarioFuncaoRepository.findByUsuarioIdAndActiveTrue(cenario.membro1Pascom.getId()))
                .extracting(h -> h.getFuncao().getId()).containsExactly(som.getId());
    }

    @Test
    void leituraDasHabilidadesVaiParaQuemVeAPastoralESoCoordenadorAltera() throws Exception {
        pedir(cenario.membro2Pascom, get("/api/pastorais/{p}/membros/{u}/habilidades",
                cenario.pascom.getId(), cenario.membro1Pascom.getId()), null).andExpect(status().isOk());
        pedir(cenario.coordenadorEcc, get("/api/pastorais/{p}/membros/{u}/habilidades",
                cenario.pascom.getId(), cenario.membro1Pascom.getId()), null).andExpect(status().isNotFound());

        conjunto(cenario.membro2Pascom, cenario.membro1Pascom, String.valueOf(som.getId())).andExpect(status().isForbidden());
        conjunto(cenario.coordenadorEcc, cenario.membro1Pascom, String.valueOf(som.getId())).andExpect(status().isNotFound());
        // pessoa que não é da pastoral: 404
        conjunto(cenario.coordenadorPascom, cenario.membroEcc, String.valueOf(som.getId())).andExpect(status().isNotFound());
    }

    @Test
    void campoAMaisNoCorpoDa400() throws Exception {
        pedir(cenario.coordenadorPascom, put("/api/pastorais/{p}/membros/{u}/habilidades",
                cenario.pascom.getId(), cenario.membro1Pascom.getId()),
                "{\"funcaoIds\":[" + som.getId() + "],\"papel\":\"COORDENADOR\"}").andExpect(status().isBadRequest());
        pedir(cenario.coordenadorPascom, post("/api/usuarios-funcoes"),
                "{\"usuarioId\":" + cenario.membro1Pascom.getId() + ",\"funcaoId\":" + som.getId() + ",\"active\":false}")
                .andExpect(status().isBadRequest());
    }
}
