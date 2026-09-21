package br.com.zep.servio.repository;

import br.com.zep.servio.model.Vaga;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface VagaRepository extends JpaRepository<Vaga, Long> {

    List<Vaga> findByCelebracaoIdAndActiveTrue(Long celebracaoId);
}
