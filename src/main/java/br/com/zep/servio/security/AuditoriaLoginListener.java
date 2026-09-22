package br.com.zep.servio.security;

import br.com.zep.servio.model.AuditLog;
import br.com.zep.servio.repository.AuditLogRepository;
import br.com.zep.servio.repository.UsuarioRepository;
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
        String email = String.valueOf(evento.getAuthentication().getName());
        log.warn("Falha de login para '{}': {}", email, evento.getException().getClass().getSimpleName());

        AuditLog log = new AuditLog();
        log.setTipoEvento("LOGIN_FALHA");
        usuarioRepository.findByEmailIgnoreCaseAndActiveTrue(email).ifPresent(usuario -> {
            log.setUsuario(usuario);
            log.setReferenciaId(usuario.getId());
            log.setParoquiaId(usuario.getParoquiaId());
        });
        auditLogRepository.save(log);
    }
}
