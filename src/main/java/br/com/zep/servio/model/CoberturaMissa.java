package br.com.zep.servio.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Qual horário de missa uma pastoral cobre. Sem linha ativa, a pastoral não recebe vaga nas missas geradas. */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cobertura_missa")
public class CoberturaMissa extends TenantEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pastoral_id", nullable = false)
    private Pastoral pastoral;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "horario_missa_id", nullable = false)
    private HorarioMissa horarioMissa;
}
