package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.TipoNotificacao;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Registro de e-mail/lembrete já enviado (Etapa 6, Parte 1): evita duplicidade se um job
 * reprocessar ou um evento for publicado duas vezes. Não é TenantEntity/BaseEntity: a tabela
 * não tem is_active/version/created_at/updated_at, só o essencial pra deduplicar.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notificacao_enviada")
public class NotificacaoEnviada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paroquia_id", nullable = false)
    private Long paroquiaId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alocacao_id", nullable = false)
    private Alocacao alocacao;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TipoNotificacao tipo;

    @Column(name = "enviada_em", nullable = false)
    private LocalDateTime enviadaEm;
}
