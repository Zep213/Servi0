package br.com.zep.servio.security;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.util.Telefone;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Autentica pelo e-mail ou pelo telefone do usuário. Os dois são únicos entre ativos no sistema todo
 * (índices uq_usuario_email_ativo e uq_usuario_telefone_ativo). O username do principal continua sendo o e-mail.
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository repository;

    /** Com {@code @} é e-mail; sem, tenta como telefone (qualquer formato aceito pelo {@link Telefone}). */
    @Transactional(readOnly = true)
    public Optional<Usuario> buscar(String login) {
        if (login == null) {
            return Optional.empty();
        }
        String texto = login.trim();
        if (texto.contains("@")) {
            return repository.findByEmailIgnoreCaseAndActiveTrue(texto);
        }
        return Telefone.normalizar(texto).flatMap(repository::findByTelefoneAndActiveTrue);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        Usuario usuario = buscar(login)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));

        return new UsuarioPrincipal(
                usuario.getId(),
                usuario.getParoquiaId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getSenha(),
                usuario.getPerfil());
    }
}
