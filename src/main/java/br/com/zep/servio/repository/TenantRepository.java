package br.com.zep.servio.repository;

import br.com.zep.servio.model.TenantEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

/**
 * Base dos repositórios de entidades da paróquia.
 * Todo acesso passa pelo paroquia_id: é o que impede uma paróquia de enxergar dados de outra.
 */
@NoRepositoryBean
public interface TenantRepository<E extends TenantEntity> extends JpaRepository<E, Long> {

    Optional<E> findByIdAndParoquiaIdAndActiveTrue(Long id, Long paroquiaId);

    Page<E> findByParoquiaIdAndActiveTrue(Long paroquiaId, Pageable pageable);
}
