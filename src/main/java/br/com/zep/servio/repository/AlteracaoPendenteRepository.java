package br.com.zep.servio.repository;

import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AlteracaoPendenteRepository extends TenantRepository<AlteracaoPendente> {

    List<AlteracaoPendente> findByPastoralIdAndStatusAndActiveTrue(Long pastoralId, StatusAlteracaoPendente status);

    /** Parte 4: só das pastorais que o usuário gerencia (COORDENADOR/VICE); todas para PADRE/ADMIN. */
    Page<AlteracaoPendente> findByParoquiaIdAndPastoralIdInAndActiveTrue(
            Long paroquiaId, List<Long> pastoraisIds, Pageable pageable);
}
