package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Quando o vice altera uma escala, a alteração já é aplicada na Alocacao e o convite já
 * sai; este registro fica pendente até o coordenador confirmar ou desfazer.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "alteracao_pendente")
public class AlteracaoPendente extends TenantEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alocacao_id", nullable = false)
    private Alocacao alocacao;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pastoral_id", nullable = false)
    private Pastoral pastoral;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vaga_anterior_id", nullable = false)
    private Vaga vagaAnterior;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_anterior_id", nullable = false)
    private Usuario usuarioAnterior;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vaga_nova_id", nullable = false)
    private Vaga vagaNova;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_novo_id", nullable = false)
    private Usuario usuarioNovo;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusAlteracaoPendente status = StatusAlteracaoPendente.PENDENTE;
}
