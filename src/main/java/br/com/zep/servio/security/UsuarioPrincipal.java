package br.com.zep.servio.security;

import br.com.zep.servio.model.enumerated.Perfil;
import lombok.Getter;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Usuário autenticado. Carrega id, paróquia e perfil para não consultar o banco a cada uso.
 * A senha é apagada logo após o login (CredentialsContainer), pois este objeto
 * será gravado na sessão a partir da etapa 3.
 */
@Getter
public class UsuarioPrincipal implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final Long paroquiaId;
    private final String nome;
    private final String email;
    private String senha;
    private final Perfil perfil;

    public UsuarioPrincipal(Long id, Long paroquiaId, String nome, String email, String senha, Perfil perfil) {
        this.id = id;
        this.paroquiaId = paroquiaId;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.perfil = perfil;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.name()));
    }

    @Override
    public String getPassword() {
        return senha;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        this.senha = null;
    }
}
