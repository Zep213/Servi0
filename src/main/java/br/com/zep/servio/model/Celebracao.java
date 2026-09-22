package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.TipoData;
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
@Table(name = "celebracao",
        indexes = @Index(name = "idx_celebracao_comunidade_data", columnList = "comunidade_id, data"))
public class Celebracao extends TenantEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comunidade_id", nullable = false)
    private Comunidade comunidade;

    @NotNull
    @Column(nullable = false)
    private LocalDate data;

    @NotNull
    @Column(nullable = false)
    private LocalTime hora;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_data", nullable = false, length = 20)
    private TipoData tipoData = TipoData.NORMAL;
}
