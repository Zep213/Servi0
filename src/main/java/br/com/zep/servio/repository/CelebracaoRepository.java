package br.com.zep.servio.repository;

import br.com.zep.servio.model.Celebracao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface CelebracaoRepository extends JpaRepository<Celebracao, Long> {

    List<Celebracao> findByComunidadeIdAndDataBetweenAndActiveTrue(Long comunidadeId, LocalDate inicio, LocalDate fim);
}
