package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.StatusConvite;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "alocacao",
        indexes = @Index(name = "idx_alocacao_usuario", columnList = "usuario_id"))
public class Alocacao extends TenantEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vaga_id", nullable = false)
    private Vaga vaga;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusConvite status = StatusConvite.PENDENTE;

    @Column(name = "data_limite_resposta")
    private LocalDateTime dataLimiteResposta;
}
