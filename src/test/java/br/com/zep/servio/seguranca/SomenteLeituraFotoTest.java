package br.com.zep.servio.seguranca;

import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.security.SomenteLeituraFilter;
import br.com.zep.servio.security.UsuarioPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** As rotas de foto são PUT/DELETE: entram na regra que já bloqueia toda escrita das contas de demonstração. */
class SomenteLeituraFotoTest {

    private final SomenteLeituraFilter filtro = new SomenteLeituraFilter("demo@servio.dev");

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    private int status(String metodo, String email) throws Exception {
        UsuarioPrincipal principal = new UsuarioPrincipal(1L, 1L, "Demo", email, null, Perfil.SERVIDOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsuarioPasswordToken(principal));
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/api/me/foto");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    private static final class UsuarioPasswordToken extends UsernamePasswordAuthenticationToken {
        UsuarioPasswordToken(UsuarioPrincipal principal) {
            super(principal, null, principal.getAuthorities());
        }
    }

    @Test
    void contaDeDemonstracaoNaoEnviaNemRemoveFoto() throws Exception {
        assertEquals(403, status("PUT", "demo@servio.dev"));
        assertEquals(403, status("DELETE", "demo@servio.dev"));
    }

    @Test
    void outraContaPassaPeloFiltro() throws Exception {
        assertEquals(200, status("PUT", "maria@servio.dev"));
    }
}
