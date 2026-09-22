package br.com.zep.servio.repository;

import br.com.zep.servio.model.CompromissoAgenda;
import java.time.LocalDate;
import java.util.List;
public interface CompromissoAgendaRepository extends TenantRepository<CompromissoAgenda> {

    List<CompromissoAgenda> findByPadreIdAndDataBetweenAndActiveTrue(Long padreId, LocalDate inicio, LocalDate fim);
}
