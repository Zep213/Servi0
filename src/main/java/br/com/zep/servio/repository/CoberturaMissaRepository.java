package br.com.zep.servio.repository;

import br.com.zep.servio.model.CoberturaMissa;

import java.util.List;

public interface CoberturaMissaRepository extends TenantRepository<CoberturaMissa> {

    List<CoberturaMissa> findByPastoralIdAndActiveTrue(Long pastoralId);

    List<CoberturaMissa> findByHorarioMissaIdAndActiveTrue(Long horarioMissaId);

    boolean existsByPastoralIdAndHorarioMissaIdAndActiveTrue(Long pastoralId, Long horarioMissaId);
}
