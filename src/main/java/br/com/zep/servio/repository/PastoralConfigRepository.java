package br.com.zep.servio.repository;

import br.com.zep.servio.model.PastoralConfig;
import java.util.List;
import java.util.Optional;

public interface PastoralConfigRepository extends TenantRepository<PastoralConfig> {

    List<PastoralConfig> findByPastoralIdAndActiveTrue(Long pastoralId);

    Optional<PastoralConfig> findByPastoralIdAndChaveAndActiveTrue(Long pastoralId, String chave);
}
