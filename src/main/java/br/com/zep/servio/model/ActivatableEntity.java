package br.com.zep.servio.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/** BaseEntity com exclusão lógica (is_active). Só o AuditLog não a utiliza. */
@Getter
@Setter
@MappedSuperclass
public abstract class ActivatableEntity extends BaseEntity {

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
