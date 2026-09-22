package br.com.zep.servio.repository;

import br.com.zep.servio.model.Comunidade;
import java.util.List;
public interface ComunidadeRepository extends TenantRepository<Comunidade> {

    List<Comunidade> findByParoquiaIdAndActiveTrue(Long paroquiaId);
}
