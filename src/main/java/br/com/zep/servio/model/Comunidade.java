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
public class Comunidade extends TenantEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String nome;
}
