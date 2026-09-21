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
@Table(name = "paroquia")
public class Paroquia extends ActivatableEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String nome;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(name = "email_contato", nullable = false)
    private String emailContato;

    @Size(max = 50)
    @Column(length = 50)
    private String telefone;
}
