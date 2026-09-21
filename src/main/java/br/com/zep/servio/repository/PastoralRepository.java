package br.com.zep.servio.repository;

import br.com.zep.servio.model.Pastoral;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface PastoralRepository extends JpaRepository<Pastoral, Long> {

    List<Pastoral> findByParoquiaIdAndActiveTrue(Long paroquiaId);
}
