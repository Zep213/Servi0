package br.com.zep.servio.repository;

import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;

import java.util.List;

public interface AlteracaoPendenteRepository extends TenantRepository<AlteracaoPendente> {

    List<AlteracaoPendente> findByPastoralIdAndStatusAndActiveTrue(Long pastoralId, StatusAlteracaoPendente status);
}
