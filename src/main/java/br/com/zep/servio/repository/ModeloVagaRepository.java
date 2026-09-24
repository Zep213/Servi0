package br.com.zep.servio.repository;

import br.com.zep.servio.model.ModeloVaga;
import java.util.List;
import java.util.Optional;

public interface ModeloVagaRepository extends TenantRepository<ModeloVaga> {

    List<ModeloVaga> findByPastoralIdAndActiveTrue(Long pastoralId);

    Optional<ModeloVaga> findByIdAndPastoralIdAndActiveTrue(Long id, Long pastoralId);
}
