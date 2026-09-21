package br.com.zep.servio.repository;

import br.com.zep.servio.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailAndParoquiaId(String email, Long paroquiaId);

    boolean existsByEmailAndParoquiaId(String email, Long paroquiaId);

    List<Usuario> findByParoquiaIdAndActiveTrue(Long paroquiaId);
}
