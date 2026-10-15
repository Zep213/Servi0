package br.com.zep.servio.repository;

import br.com.zep.servio.model.HorarioMissa;

import org.springframework.data.jpa.repository.Query;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HorarioMissaRepository extends TenantRepository<HorarioMissa> {

    /** Todos os horários ativos de todas as paróquias (o job de geração roda sem usuário logado). */
    @Query("select h.id from HorarioMissa h where h.active = true order by h.id")
    List<Long> idsAtivos();

    List<HorarioMissa> findByComunidadeIdAndActiveTrueOrderByDiaSemanaAscHoraAsc(Long comunidadeId);

    List<HorarioMissa> findByParoquiaIdAndActiveTrueOrderByComunidadeIdAscDiaSemanaAscHoraAsc(Long paroquiaId);

    List<HorarioMissa> findByParoquiaIdAndIdInAndActiveTrue(Long paroquiaId, Collection<Long> ids);

    Optional<HorarioMissa> findByIdAndComunidadeIdAndActiveTrue(Long id, Long comunidadeId);

    /** Unicidade no criar e no atualizar (o id atual não conta como duplicata). */
    boolean existsByComunidadeIdAndDiaSemanaAndHoraAndActiveTrueAndIdNot(Long comunidadeId, short diaSemana,
                                                                         LocalTime hora, Long id);
}
