package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.Perfil;
import jakarta.validation.constraints.*;

/** Update: a senha é opcional; se vier em branco ou nula, a senha atual é mantida. */
public record UsuarioUpdateDTO(
    @NotBlank @Size(max = 255) String nome,
    @NotBlank @Email @Size(max = 255) String email,
    @Size(min = 8, max = 72) String senha,
    @NotNull Perfil perfil
) {}
