package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.seguranca.ApoioTeste;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import jakarta.servlet.http.Cookie;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mudança de papel encerra as sessões da pessoa afetada (Sprint 2, Cartão 5). Sem {@code @Transactional}: a
 * sessão só cai no commit ({@code encerrarTodasAposCommit}), que num teste transacional nunca aconteceria. Quem
 * faz a alteração age pelo principal do teste; quem tem a sessão que importa entra de verdade pelo login.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class PapelEncerraSessaoIT {

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
    PasswordEncoder passwordEncoder;

    CenarioParoquia cenario;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
    }

    private Cookie entrar(Usuario quem) throws Exception {
        Cookie sessao = ApoioTeste.login(mvc, quem.getEmail(), CenarioParoquia.SENHA);
        mvc.perform(get("/api/me").cookie(sessao)).andExpect(status().isOk());
        return sessao;
    }

    private void sessaoViva(Cookie sessao) throws Exception {
        mvc.perform(get("/api/me").cookie(sessao)).andExpect(status().isOk());
    }

    private void sessaoMorta(Cookie sessao) throws Exception {
        mvc.perform(get("/api/me").cookie(sessao)).andExpect(status().isUnauthorized());
    }

    private Long participacao(Usuario quem, Long pastoralId) {
        return usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(quem.getId(), pastoralId)
                .orElseThrow().getId();
    }

    private ResultActions pedir(Usuario ator, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                                String corpo) throws Exception {
        var construtor = req.with(user(cenario.principal(ator))).with(csrf());
        if (corpo != null) {
            construtor = construtor.contentType(MediaType.APPLICATION_JSON).content(corpo);
        }
        return mvc.perform(construtor);
    }

    private static String corpo(Usuario usuario, Long pastoralId, String papel) {
        return "{\"usuarioId\":" + usuario.getId() + ",\"pastoralId\":" + pastoralId + ",\"papel\":\"" + papel + "\"}";
    }

    @Test
    void promoverMembroACoordenadorDerrubaASessaoENovoLoginFunciona() throws Exception {
        Long pascom = cenario.pascom.getId();
        Cookie sessao = entrar(cenario.membro2Pascom);

        pedir(cenario.padreA, put("/api/usuarios-pastorais/{id}", participacao(cenario.membro2Pascom, pascom)),
                corpo(cenario.membro2Pascom, pascom, "COORDENADOR")).andExpect(status().isOk());

        sessaoMorta(sessao);
        entrar(cenario.membro2Pascom);
    }

    @Test
    void rebaixarERemoverPapelDerrubamASessaoAntiga() throws Exception {
        Long ecc = cenario.ecc.getId();
        Cookie sessao = entrar(cenario.coordenadorEcc);

        pedir(cenario.padreA, put("/api/usuarios-pastorais/{id}", participacao(cenario.coordenadorEcc, ecc)),
                corpo(cenario.coordenadorEcc, ecc, "MEMBRO")).andExpect(status().isOk());
        sessaoMorta(sessao);

        Cookie novaSessao = entrar(cenario.coordenadorEcc);
        pedir(cenario.padreA, delete("/api/usuarios-pastorais/{id}", participacao(cenario.coordenadorEcc, ecc)), null)
                .andExpect(status().isNoContent());
        sessaoMorta(novaSessao);
    }

    @Test
    void criarPapelDerrubaASessaoDaPessoaAfetada() throws Exception {
        Long pascom = cenario.pascom.getId();
        Cookie sessao = entrar(cenario.membroEcc);

        pedir(cenario.coordenadorPascom, post("/api/usuarios-pastorais"), corpo(cenario.membroEcc, pascom, "MEMBRO"))
                .andExpect(status().isCreated());

        sessaoMorta(sessao);
    }

    @Test
    void sessaoDeOutraPessoaENovoLoginDeQuemAlterouNaoCaem() throws Exception {
        Long pascom = cenario.pascom.getId();
        Cookie afetada = entrar(cenario.membro2Pascom);
        Cookie outra = entrar(cenario.membro1Pascom);
        Cookie padre = entrar(cenario.padreA);

        pedir(cenario.padreA, put("/api/usuarios-pastorais/{id}", participacao(cenario.membro2Pascom, pascom)),
                corpo(cenario.membro2Pascom, pascom, "TESOUREIRO")).andExpect(status().isOk());

        sessaoMorta(afetada);
        sessaoViva(outra);
        sessaoViva(padre);
    }

    @Test
    void alteracaoRecusadaNaoDerrubaNinguem() throws Exception {
        Long pascom = cenario.pascom.getId();
        Cookie membro2 = entrar(cenario.membro2Pascom);
        Cookie membro1 = entrar(cenario.membro1Pascom);

        // 422: a pastoral já tem os 2 secretários
        pedir(cenario.padreA, put("/api/usuarios-pastorais/{id}", participacao(cenario.membro2Pascom, pascom)),
                corpo(cenario.membro2Pascom, pascom, "SECRETARIO")).andExpect(status().isUnprocessableEntity());
        // 409: a pessoa já tem papel nesta pastoral
        pedir(cenario.coordenadorPascom, post("/api/usuarios-pastorais"), corpo(cenario.membro1Pascom, pascom, "MEMBRO"))
                .andExpect(status().isConflict());
        // 404: coordenador de outra pastoral não enxerga a Pascom
        pedir(cenario.coordenadorEcc, put("/api/usuarios-pastorais/{id}", participacao(cenario.membro2Pascom, pascom)),
                corpo(cenario.membro2Pascom, pascom, "TESOUREIRO")).andExpect(status().isNotFound());
        // 403: membro comum não gerencia papéis
        pedir(cenario.membro1Pascom, delete("/api/usuarios-pastorais/{id}", participacao(cenario.membro2Pascom, pascom)),
                null).andExpect(status().isForbidden());

        sessaoViva(membro2);
        sessaoViva(membro1);
    }

    @Test
    void salvarOMesmoPapelNaoMudaNadaENaoDerrubaASessao() throws Exception {
        Long pascom = cenario.pascom.getId();
        Cookie sessao = entrar(cenario.membro2Pascom);

        pedir(cenario.padreA, put("/api/usuarios-pastorais/{id}", participacao(cenario.membro2Pascom, pascom)),
                corpo(cenario.membro2Pascom, pascom, "MEMBRO")).andExpect(status().isOk());

        sessaoViva(sessao);
    }
}
