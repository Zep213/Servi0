package br.com.zep.servio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Bloqueia toda escrita das contas de demonstração (servio.demo.emails), mesmo sendo ADMIN.
 * Assumir/sair de paróquia fica liberado: só muda a sessão, não dados — é como o
 * visitante navega entre paróquias para "ver tudo" (Parte 2.2).
 */
public class SomenteLeituraFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_ESCRITA = Set.of("POST", "PUT", "DELETE", "PATCH");
    private static final Pattern ASSUMIR = Pattern.compile("/api/plataforma/paroquias/\\d+/assumir");

    private final Set<String> emailsSomenteLeitura;

    /** Uma ou mais contas, separadas por vírgula. */
    public SomenteLeituraFilter(String emailsSomenteLeitura) {
        this.emailsSomenteLeitura = emailsSomenteLeitura == null ? Set.of()
                : Arrays.stream(emailsSomenteLeitura.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(s -> s.toLowerCase())
                        .collect(Collectors.toSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (emailsSomenteLeitura.isEmpty() || !METODOS_ESCRITA.contains(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return "/api/plataforma/paroquias/sair".equals(uri) || ASSUMIR.matcher(uri).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal
                && emailsSomenteLeitura.contains(principal.getEmail().toLowerCase())) {
            RespostaProblema.escrever(response, 403, "Conta de demonstração: somente leitura, nenhuma alteração é permitida.");
            return;
        }
        chain.doFilter(request, response);
    }
}
