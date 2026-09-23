package br.com.zep.servio.repository;

import br.com.zep.servio.model.Reuniao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReuniaoRepository extends TenantRepository<Reuniao> {

    Page<Reuniao> findByPastoralIdAndActiveTrue(Long pastoralId, Pageable pageable);
}
