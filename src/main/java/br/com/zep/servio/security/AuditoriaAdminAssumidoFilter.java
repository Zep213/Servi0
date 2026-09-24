package br.com.zep.servio.security;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.service.AuditLogService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * Audita toda escrita feita por um ADMIN numa paróquia assumida (Parte 2.2).
 * Registrado na cadeia do Security (não como bean), como o LoginRateLimitFilter.
 */
public class AuditoriaAdminAssumidoFilter extends OncePerRequestFilter {

    private static final Set<String> METODOS_ESCRITA = Set.of("POST", "PUT", "DELETE", "PATCH");

    private final UsuarioLogado usuarioLogado;
    private final AuditLogService auditLogService;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaAdminAssumidoFilter(UsuarioLogado usuarioLogado, AuditLogService auditLogService,
            UsuarioRepository usuarioRepository) {
        this.usuarioLogado = usuarioLogado;
        this.auditLogService = auditLogService;
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // /api/plataforma/** (assumir/sair) já se audita sozinho: evita duplicar o registro.
        return !METODOS_ESCRITA.contains(request.getMethod()) || request.getRequestURI().startsWith("/api/plataforma/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } finally {
            registrarSeForAdminAssumido(request);
        }
    }

    private void registrarSeForAdminAssumido(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof UsuarioPrincipal principal)
                || principal.getPerfil() != Perfil.ADMIN) {
            return;
        }
        Long assumida = usuarioLogado.paroquiaAssumida();
        if (assumida == null) {
            return;
        }
        Usuario admin = usuarioRepository.findById(principal.getId()).orElse(null);
        String detalhe = request.getMethod() + " " + request.getRequestURI();
        auditLogService.registrar("ADMIN_ACAO_EM_PAROQUIA_ASSUMIDA", assumida, admin, assumida, detalhe);
    }
}
