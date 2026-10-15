package br.com.zep.servio.security;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.util.Telefone;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Chaves do rate limit de login a partir do que a pessoa digitou. E-mail e telefone da mesma conta
 * compartilham a chave {@code conta:<id>}, então as tentativas pelos dois se somam.
 */
@Component
@RequiredArgsConstructor
public class ChaveLogin {

    private final UsuarioDetailsService usuarios;

    /** Chave do texto digitado (e-mail minúsculo, telefone normalizado ou o próprio texto) e, se a conta existe, a da conta. */
    public List<String> chaves(String digitado) {
        List<String> chaves = new ArrayList<>(2);
        if (digitado == null || digitado.isBlank()) {
            return chaves;
        }
        chaves.add(chaveDoTexto(digitado));
        usuarios.buscar(digitado).ifPresent(u -> chaves.add(chaveConta(u.getId())));
        return chaves;
    }

    /** Tudo que um login bem-sucedido limpa além do IP: conta, e-mail e telefone da conta. */
    public List<String> chavesDaConta(Usuario usuario) {
        List<String> chaves = new ArrayList<>(3);
        chaves.add(chaveConta(usuario.getId()));
        chaves.add("login:" + usuario.getEmail().trim().toLowerCase(Locale.ROOT));
        if (usuario.getTelefone() != null) {
            chaves.add("login:" + usuario.getTelefone());
        }
        return chaves;
    }

    private static String chaveConta(Long id) {
        return "conta:" + id;
    }

    private static String chaveDoTexto(String digitado) {
        String texto = digitado.trim();
        if (!texto.contains("@")) {
            Optional<String> telefone = Telefone.normalizar(texto);
            if (telefone.isPresent()) {
                return "login:" + telefone.get();
            }
        }
        return "login:" + texto.toLowerCase(Locale.ROOT);
    }
}
