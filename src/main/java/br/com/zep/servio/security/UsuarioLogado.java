package br.com.zep.servio.security;

import br.com.zep.servio.exception.ServioException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Único ponto do sistema que sabe quem está logado.
 * Services usam esta classe; nunca leem ids de usuário/paróquia do JSON.
 */
@Component
public class UsuarioLogado {

    public UsuarioPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal) {
            return principal;
        }
        throw new ServioException("Usuário não autenticado", HttpStatus.UNAUTHORIZED);
    }

    public Long id() {
        return get().getId();
    }

    public Long paroquiaId() {
        return get().getParoquiaId();
    }
}
