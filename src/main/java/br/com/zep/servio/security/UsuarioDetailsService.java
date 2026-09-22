package br.com.zep.servio.security;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Autentica pelo e-mail do usuário. O e-mail ainda é único só por paróquia (até a etapa 2);
 * se existir ativo em mais de uma, o login é recusado por ambiguidade.
 */
@Service
@RequiredArgsConstructor
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository repository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        List<Usuario> usuarios = repository.findByEmailAndActiveTrue(email);
        if (usuarios.size() != 1) {
            throw new UsernameNotFoundException("Credenciais inválidas");
        }
        Usuario usuario = usuarios.getFirst();
        return new UsuarioPrincipal(
                usuario.getId(),
                usuario.getParoquia().getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getSenha(),
                usuario.getPerfil());
    }
}
