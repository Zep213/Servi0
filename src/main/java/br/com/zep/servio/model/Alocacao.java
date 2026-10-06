package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.OrigemAlocacao;
import br.com.zep.servio.model.enumerated.StatusConvite;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigemAlocacao origem = OrigemAlocacao.COORDENADOR;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalado_por_id")
    private Usuario escaladoPor;

    @Column(name = "respondido_em")
    private LocalDateTime respondidoEm;

    @Size(max = 500)
    @Column(length = 500)
    private String justificativa;

    @Column(name = "substituida_em")
    private LocalDateTime substituidaEm;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", columnDefinition = "CHAR(64)")
    private String tokenHash;
}
