package br.com.zep.servio.repository;

import br.com.zep.servio.model.Indisponibilidade;
import java.time.LocalDate;
import java.util.List;
public interface IndisponibilidadeRepository extends TenantRepository<Indisponibilidade> {

    /** Indisponibilidades do usuário que cruzam o período [inicio, fim]. */
    List<Indisponibilidade> findByUsuarioIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndActiveTrue(
            Long usuarioId, LocalDate fim, LocalDate inicio);
}
