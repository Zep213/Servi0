package br.com.zep.servio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "vaga")
public class Vaga extends TenantEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "celebracao_id", nullable = false)
    private Celebracao celebracao;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "funcao_id", nullable = false)
    private Funcao funcao;

    /** Nulo até a pastoral responsável definir quantas pessoas a vaga precisa (Parte 3.2). */
    @Positive
    private Integer quantidade;

    @Column(name = "horario_chegada")
    private LocalTime horarioChegada;

    @Size(max = 500)
    @Column(length = 500)
    private String observacao;
}
