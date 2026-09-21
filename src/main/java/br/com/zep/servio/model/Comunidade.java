package br.com.zep.servio.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "comunidade")
public class Comunidade extends ActivatableEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String nome;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paroquia_id", nullable = false)
    private Paroquia paroquia;
}
