package br.com.zep.servio.security;

import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.enumerated.Perfil;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Único ponto do sistema que sabe quem está logado.
 * Services usam esta classe; nunca leem ids de usuário/paróquia do JSON.
 */
@Component
public class UsuarioLogado {

    private static final String ATRIBUTO_PAROQUIA_ASSUMIDA = "paroquiaAssumidaId";

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

    /**
     * A paróquia efetiva do usuário: a assumida, se for ADMIN e houver uma assumida
     * na sessão; senão, a própria. Todo o resto do sistema filtra por este valor sem
     * precisar saber que existe "assumir paróquia".
     */
    public Long paroquiaId() {
        UsuarioPrincipal usuario = get();
        if (usuario.getPerfil() == Perfil.ADMIN) {
            Long assumida = paroquiaAssumida();
            if (assumida != null) {
                return assumida;
            }
        }
        return usuario.getParoquiaId();
    }

    /** A paróquia assumida na sessão atual, ou null se nenhuma (ou se não é ADMIN). */
    public Long paroquiaAssumida() {
        HttpSession sessao = sessaoAtual(false);
        return sessao == null ? null : (Long) sessao.getAttribute(ATRIBUTO_PAROQUIA_ASSUMIDA);
    }

    public void assumir(Long paroquiaId) {
        sessaoAtual(true).setAttribute(ATRIBUTO_PAROQUIA_ASSUMIDA, paroquiaId);
    }

    public void sair() {
        HttpSession sessao = sessaoAtual(false);
        if (sessao != null) {
            sessao.removeAttribute(ATRIBUTO_PAROQUIA_ASSUMIDA);
        }
    }

    private HttpSession sessaoAtual(boolean criar) {
        var atributos = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return atributos == null ? null : atributos.getRequest().getSession(criar);
    }
}
