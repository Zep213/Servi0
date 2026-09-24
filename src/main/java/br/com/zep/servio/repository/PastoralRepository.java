package br.com.zep.servio.repository;

import br.com.zep.servio.model.Pastoral;
import java.util.List;
public interface PastoralRepository extends TenantRepository<Pastoral> {

    List<Pastoral> findByParoquiaIdAndActiveTrue(Long paroquiaId);

    long countByParoquiaIdAndActiveTrue(Long paroquiaId);
}
