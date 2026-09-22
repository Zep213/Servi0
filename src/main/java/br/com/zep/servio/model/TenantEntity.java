package br.com.zep.servio.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Entidade que pertence a uma paróquia.
 * O paroquiaId é preenchido pelo CrudService a partir do usuário logado, nunca pelo cliente.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantEntity extends ActivatableEntity {

    @Column(name = "paroquia_id", nullable = false, updatable = false)
    private Long paroquiaId;
}
