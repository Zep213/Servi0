package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AutorizacaoIT {

    @Autowired
    MockMvc mvc;

    static UsuarioPrincipal logado(Perfil perfil) {
        return new UsuarioPrincipal(1L, 1L, "Teste", "teste@servio.dev", null, perfil);
    }

    // /api/alocacoes, /api/usuarios-pastorais e POST /api/usuarios não entram aqui: a escrita
    // neles só exige login no SecurityConfig e quem decide é a regra fina no service (papel na
    // pastoral, ou perfil-alvo pedido em /api/usuarios) — checada depois da validação do DTO,
    // não antes, então corpo vazio dá 400 e não 403. Ver UsuarioContaIT para essas regras finas.
    static Stream<Arguments> escritasProibidas() {
        return Stream.of(
                Arguments.of(Perfil.SERVIDOR, "/api/celebracoes"),
                Arguments.of(Perfil.SERVIDOR, "/api/paroquias/minha"),
                Arguments.of(Perfil.SERVIDOR, "/api/compromissos-agenda"));
    }

    @ParameterizedTest(name = "{0} não pode escrever em {1}")
    @MethodSource("escritasProibidas")
    void perfilSemPermissaoRecebe403(Perfil perfil, String rota) throws Exception {
        mvc.perform(post(rota).with(user(logado(perfil))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());   // 403, e não 400: a regra roda antes da validação
    }

    @Test
    void semLoginRecebe401() throws Exception {
        mvc.perform(get("/api/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void rotaNaoMapeadaEhBloqueada() throws Exception {
        mvc.perform(get("/api/rota-que-nao-existe").with(user(logado(Perfil.ADMIN))))
                .andExpect(status().isForbidden());
    }
}
