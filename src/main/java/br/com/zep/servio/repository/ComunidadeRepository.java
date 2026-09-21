package br.com.zep.servio.repository;

import br.com.zep.servio.model.Comunidade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ComunidadeRepository extends JpaRepository<Comunidade, Long> {

    List<Comunidade> findByParoquiaIdAndActiveTrue(Long paroquiaId);
}
