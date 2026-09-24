package br.com.zep.servio.repository;

import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.enumerated.PapelPastoral;

import java.util.List;
import java.util.Optional;

public interface UsuarioPastoralRepository extends TenantRepository<UsuarioPastoral> {

    List<UsuarioPastoral> findByPastoralIdAndActiveTrue(Long pastoralId);

    List<UsuarioPastoral> findByPastoralIdAndPapelAndActiveTrue(Long pastoralId, PapelPastoral papel);

    Optional<UsuarioPastoral> findByUsuarioIdAndPastoralIdAndActiveTrue(Long usuarioId, Long pastoralId);

    List<UsuarioPastoral> findByUsuarioIdAndActiveTrue(Long usuarioId);

    boolean existsByUsuarioIdAndPastoralIdAndActiveTrueAndIdNot(Long usuarioId, Long pastoralId, Long id);

    boolean existsByUsuarioIdAndPapelAndActiveTrue(Long usuarioId, PapelPastoral papel);

    long countByPastoralIdAndPapelAndActiveTrueAndIdNot(Long pastoralId, PapelPastoral papel, Long id);
}
