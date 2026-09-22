package br.com.zep.servio.repository;

import br.com.zep.servio.model.Funcao;
import java.util.List;
public interface FuncaoRepository extends TenantRepository<Funcao> {

    List<Funcao> findByPastoralIdAndActiveTrue(Long pastoralId);
}
