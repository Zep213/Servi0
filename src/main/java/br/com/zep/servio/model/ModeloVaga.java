package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.TipoCelebracaoModelo;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "modelo_vaga")
public class ModeloVaga extends TenantEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pastoral_id", nullable = false)
    private Pastoral pastoral;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funcao_id", nullable = false)
    private Funcao funcao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_celebracao", nullable = false, length = 20)
    private TipoCelebracaoModelo tipoCelebracao;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer quantidade;
}
