package br.com.zep.servio.repository;

import br.com.zep.servio.model.Indisponibilidade;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface IndisponibilidadeRepository extends TenantRepository<Indisponibilidade> {

    /** Indisponibilidades do usuário que cruzam o período [inicio, fim]. */
    List<Indisponibilidade> findByUsuarioIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndActiveTrue(
            Long usuarioId, LocalDate fim, LocalDate inicio);

    /** Só as indisponibilidades de um usuário (quem não gerencia a escala só vê as próprias). */
    Page<Indisponibilidade> findByParoquiaIdAndUsuarioIdAndActiveTrue(Long paroquiaId, Long usuarioId, Pageable pageable);
}
