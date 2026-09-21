package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.Perfil;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario",
        uniqueConstraints = @UniqueConstraint(columnNames = {"email", "paroquia_id"}))
public class Usuario extends ActivatableEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String nome;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false)
    private String email;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String senha;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Perfil perfil;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paroquia_id", nullable = false)
    private Paroquia paroquia;
}
