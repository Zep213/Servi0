package br.com.zep.servio.repository;

import br.com.zep.servio.model.LancamentoFinanceiro;
import br.com.zep.servio.model.enumerated.TipoLancamento;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface LancamentoFinanceiroRepository extends TenantRepository<LancamentoFinanceiro> {

    Page<LancamentoFinanceiro> findByPastoralIdAndActiveTrue(Long pastoralId, Pageable pageable);

    List<LancamentoFinanceiro> findByPastoralIdAndTipoAndActiveTrue(Long pastoralId, TipoLancamento tipo);
}
