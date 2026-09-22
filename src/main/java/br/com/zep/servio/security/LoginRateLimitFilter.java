package br.com.zep.servio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Barra força bruta no login. Registrado na cadeia do Security (não como bean),
 * senão o Spring Boot o adicionaria também fora dela e ele rodaria duas vezes.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final TentativasLogin tentativas;

    public LoginRateLimitFilter(TentativasLogin tentativas) {
        this.tentativas = tentativas;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String email = request.getParameter("email");
        boolean bloqueado = tentativas.bloqueado(TentativasLogin.chaveIp(request))
                || (email != null && tentativas.bloqueado(TentativasLogin.chaveEmail(email)));

        if (bloqueado) {
            response.setHeader("Retry-After", String.valueOf(TentativasLogin.JANELA.toSeconds()));
            RespostaProblema.escrever(response, 429, "Muitas tentativas. Aguarde alguns minutos e tente de novo.");
            return;
        }
        chain.doFilter(request, response);
    }
}
