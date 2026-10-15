package br.com.zep.servio.security;

import br.com.zep.servio.model.AuditLog;
import br.com.zep.servio.repository.AuditLogRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.util.Telefone;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Registra logins bem e mal sucedidos. Nunca grava a senha digitada. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditoriaLoginListener {

    private final AuditLogRepository auditLogRepository;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioDetailsService usuarioDetailsService;

    @EventListener
    @Transactional
    public void aoEntrar(AuthenticationSuccessEvent evento) {
        if (!(evento.getAuthentication().getPrincipal() instanceof UsuarioPrincipal principal)) {
            return;
        }
        AuditLog log = new AuditLog();
        log.setTipoEvento("LOGIN_SUCESSO");
        log.setReferenciaId(principal.getId());
        log.setParoquiaId(principal.getParoquiaId());
        usuarioRepository.findById(principal.getId()).ifPresent(log::setUsuario);
        auditLogRepository.save(log);
    }

    @EventListener
    @Transactional
    public void aoFalhar(AbstractAuthenticationFailureEvent evento) {
        String login = String.valueOf(evento.getAuthentication().getName());
        log.warn("Falha de login para '{}': {}", paraLog(login), evento.getException().getClass().getSimpleName());

        AuditLog log = new AuditLog();
        log.setTipoEvento("LOGIN_FALHA");
        usuarioDetailsService.buscar(login).ifPresent(usuario -> {
            log.setUsuario(usuario);
            log.setReferenciaId(usuario.getId());
            log.setParoquiaId(usuario.getParoquiaId());
        });
        auditLogRepository.save(log);
    }

    /**
     * E-mail aparece como digitado; telefone é mascarado (ex.: +55839****4407). Qualquer outra coisa
     * (inclusive senha digitada no campo de login) nunca vai pro log.
     */
    private static String paraLog(String login) {
        if (login.length() <= 255 && login.matches("[^@\\s]+@[^@\\s]+")) {
            return login;
        }
        return Telefone.normalizar(login)
                .map(t -> t.substring(0, 6) + "****" + t.substring(t.length() - 4))
                .orElse("[login fora do formato]");
    }
}
