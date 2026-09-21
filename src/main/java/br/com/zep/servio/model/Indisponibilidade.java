package br.com.zep.servio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "indisponibilidade")
public class Indisponibilidade extends ActivatableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @NotNull
    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Size(max = 500)
    @Column(length = 500)
    private String motivo;

    @AssertTrue(message = "dataFim deve ser igual ou posterior a dataInicio")
    private boolean isPeriodoValido() {
        return dataInicio == null || dataFim == null || !dataFim.isBefore(dataInicio);
    }
}
