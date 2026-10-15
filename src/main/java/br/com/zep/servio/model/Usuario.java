package br.com.zep.servio.model;

import br.com.zep.servio.model.enumerated.Aparencia;
import br.com.zep.servio.model.enumerated.Perfil;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "usuario")
public class Usuario extends TenantEntity {

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false)
    private String nome;

    @NotBlank
    @Email
    @Size(max = 255)
    @Column(nullable = false)
    private String email;

    /** Hash BCrypt (o limite de 72 vale no DTO, não aqui). Nulo enquanto a conta não foi ativada, ou depois de "Proteger minha conta". */
    @Size(max = 255)
    private String senha;

    /** Quando a pessoa definiu a senha pelo link de ativação (contas antigas: data da migration V18). */
    @Column(name = "ativado_em")
    private LocalDateTime ativadoEm;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Perfil perfil;

    /** Normalizado (+55DDNNNNNNNNN); só o próprio usuário define, pelo PUT /api/me. */
    @Size(max = 20)
    @Column(length = 20)
    private String telefone;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Aparencia aparencia = Aparencia.AUTOMATICO;

    /** Quantas vezes a pessoa pulou o pedido de telefone. */
    @Column(name = "telefone_pulos", nullable = false)
    private short telefonePulos;

    /** Conta que ainda espera a ativação: sem senha e nunca ativada. */
    public boolean estaAtivada() {
        return senha != null || ativadoEm != null;
    }
}
