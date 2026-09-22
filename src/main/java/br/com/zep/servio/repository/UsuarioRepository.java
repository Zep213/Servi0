package br.com.zep.servio.repository;

import br.com.zep.servio.model.Usuario;

import java.util.Optional;

public interface UsuarioRepository extends TenantRepository<Usuario> {

    /** Login: e-mail é único entre ativos no sistema todo (índice uq_usuario_email_ativo). */
    Optional<Usuario> findByEmailIgnoreCaseAndActiveTrue(String email);

    /** Unicidade no criar e no atualizar (o id atual não conta como duplicata). */
    boolean existsByEmailIgnoreCaseAndActiveTrueAndIdNot(String email, Long id);
}
