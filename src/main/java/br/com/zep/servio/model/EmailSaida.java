package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.StatusEmail;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Um e-mail a enviar (fila/outbox, Sprint 1 Cartão 1). Não é {@code TenantEntity}: a tabela não
 * tem is_active/version, só o essencial pra enviar e tentar de novo se falhar. {@code paroquiaId}
 * é opcional (alguns envios, como o pedido de reunião, já carregam a paróquia de quem pediu).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "email_saida")
public class EmailSaida {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paroquia_id")
    private Long paroquiaId;

    @NotBlank
    @Column(nullable = false)
    private String destinatario;

    @NotBlank
    @Column(nullable = false)
    private String assunto;

    /** Nulo = texto simples, em {@code variaveis.get("texto")}. */
    private String template;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> variaveis;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusEmail status = StatusEmail.PENDENTE;

    @Column(nullable = false)
    private short tentativas;

    @Column(name = "proxima_tentativa_em", nullable = false)
    private LocalDateTime proximaTentativaEm;

    @Column(name = "ultimo_erro", length = 500)
    private String ultimoErro;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "enviado_em")
    private LocalDateTime enviadoEm;
}
