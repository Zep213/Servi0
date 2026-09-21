package br.com.zep.servio.repository;

import br.com.zep.servio.model.PedidoTroca;
import org.springframework.data.jpa.repository.JpaRepository;
import br.com.zep.servio.model.enumerated.StatusTroca;
import java.util.List;
public interface PedidoTrocaRepository extends JpaRepository<PedidoTroca, Long> {

    List<PedidoTroca> findByStatusAndActiveTrue(StatusTroca status);

    List<PedidoTroca> findBySolicitanteIdAndActiveTrue(Long solicitanteId);
}
