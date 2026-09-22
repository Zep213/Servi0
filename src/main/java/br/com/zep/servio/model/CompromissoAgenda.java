package br.com.zep.servio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "compromisso_agenda")
public class CompromissoAgenda extends TenantEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "padre_id", nullable = false)
    private Usuario padre;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String titulo;

    @NotNull
    @Column(nullable = false)
    private LocalDate data;

    private LocalTime hora;

    @Size(max = 100)
    @Column(length = 100)
    private String tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidade_id")
    private Comunidade comunidade;

    @Column(nullable = false)
    private boolean privado = false;
}
