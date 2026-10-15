package br.com.zep.servio.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Horário fixo de missa de uma comunidade (padrão: domingo). O job de geração (cartão 2) cria as celebrações a
 * partir dele. {@code diaSemana} segue {@link DayOfWeek}: 1 = segunda ... 7 = domingo.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "horario_missa")
public class HorarioMissa extends TenantEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comunidade_id", nullable = false)
    private Comunidade comunidade;

    @Column(name = "dia_semana", nullable = false)
    private short diaSemana = (short) DayOfWeek.SUNDAY.getValue();

    @NotNull
    @Column(nullable = false)
    private LocalTime hora;

    public DayOfWeek diaDaSemana() {
        return DayOfWeek.of(diaSemana);
    }
}
