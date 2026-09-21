package br.com.zep.servio.repository;

import br.com.zep.servio.model.Indisponibilidade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface IndisponibilidadeRepository extends JpaRepository<Indisponibilidade, Long> {

    /** Indisponibilidades do usuário que cruzam o período [inicio, fim]. */
    List<Indisponibilidade> findByUsuarioIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndActiveTrue(
            Long usuarioId, LocalDate fim, LocalDate inicio);
}
