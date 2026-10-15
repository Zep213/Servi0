package br.com.zep.servio.repository;

import br.com.zep.servio.model.UsuarioFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioFotoRepository extends JpaRepository<UsuarioFoto, Long> {

    /** Só a data (sem carregar os bytes): serve ao {@code fotoUrl}. */
    @Query("select f.atualizadoEm from UsuarioFoto f where f.usuarioId = :usuarioId")
    Optional<LocalDateTime> atualizadoEm(@Param("usuarioId") Long usuarioId);

    /** Pares [usuarioId, atualizadoEm] das pessoas que têm foto, para listas (membros da pastoral). */
    @Query("select f.usuarioId, f.atualizadoEm from UsuarioFoto f where f.usuarioId in :ids")
    List<Object[]> datasDeFotos(@Param("ids") Collection<Long> ids);
}
