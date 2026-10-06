package br.com.zep.servio.repository;

import br.com.zep.servio.model.Funcao;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface FuncaoRepository extends TenantRepository<Funcao> {

    List<Funcao> findByPastoralIdAndActiveTrue(Long pastoralId);

    /** Funções de uma pastoral, da paróquia (filtro de GET /api/funcoes?pastoralId=). */
    Page<Funcao> findByParoquiaIdAndPastoralIdAndActiveTrue(Long paroquiaId, Long pastoralId, Pageable pageable);
}
