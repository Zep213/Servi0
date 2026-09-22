package br.com.zep.servio.repository;

import br.com.zep.servio.model.Vaga;
import java.util.List;
public interface VagaRepository extends TenantRepository<Vaga> {

    List<Vaga> findByCelebracaoIdAndActiveTrue(Long celebracaoId);
}
