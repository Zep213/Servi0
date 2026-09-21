package br.com.zep.servio.repository;

import br.com.zep.servio.model.CompromissoAgenda;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface CompromissoAgendaRepository extends JpaRepository<CompromissoAgenda, Long> {

    List<CompromissoAgenda> findByPadreIdAndDataBetweenAndActiveTrue(Long padreId, LocalDate inicio, LocalDate fim);
}
