package br.com.zep.servio.repository;

import br.com.zep.servio.model.Funcao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FuncaoRepository extends JpaRepository<Funcao, Long> {

    List<Funcao> findByPastoralIdAndActiveTrue(Long pastoralId);
}
