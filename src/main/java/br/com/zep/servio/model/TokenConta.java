package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.TipoTokenConta;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Token de ativação, recuperação ou proteção de uma conta (Sprint 2, Cartão 1). Não é
 * {@code TenantEntity}: a tabela não tem is_active/version (como {@code EmailSaida}); a paróquia vem do usuário.
 * Só o hash do token é guardado.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "token_conta")
public class TokenConta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoTokenConta tipo;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Column(name = "usado_em")
    private LocalDateTime usadoEm;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;
}
