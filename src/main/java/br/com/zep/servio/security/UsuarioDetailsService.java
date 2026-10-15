package br.com.zep.servio.security;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.util.Telefone;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Autentica pelo e-mail ou pelo telefone do usuário. Os dois são únicos entre ativos no sistema todo
 * (índices uq_usuario_email_ativo e uq_usuario_telefone_ativo). O username do principal continua sendo o e-mail.
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    /**
     * Hash de uma senha aleatória descartada: conta sem senha (ainda não ativada) passa por ele, para a
     * 401 e o tempo do BCrypt serem iguais aos de senha errada. Nunca entregar {@code null} ao Spring, que
     * pularia o BCrypt e denunciaria quem tem conta pendente. Mesmo custo do PasswordEncoder da aplicação.
     */
    private static final String HASH_SEM_SENHA =
            new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

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
                usuario.getSenha() != null ? usuario.getSenha() : HASH_SEM_SENHA,
                usuario.getPerfil());
    }
}
