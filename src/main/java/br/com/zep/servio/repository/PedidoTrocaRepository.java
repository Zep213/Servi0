package br.com.zep.servio.repository;

import br.com.zep.servio.model.PedidoTroca;
import br.com.zep.servio.model.enumerated.StatusTroca;
import java.util.List;
public interface PedidoTrocaRepository extends TenantRepository<PedidoTroca> {

    List<PedidoTroca> findByStatusAndActiveTrue(StatusTroca status);

    List<PedidoTroca> findBySolicitanteIdAndActiveTrue(Long solicitanteId);
}
