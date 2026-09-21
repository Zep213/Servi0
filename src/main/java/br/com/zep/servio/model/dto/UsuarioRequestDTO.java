package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.Perfil;
import jakarta.validation.constraints.*;

public record UsuarioRequestDTO(
    @NotBlank
    @Size(max = 255)
    String nome,
    @NotBlank
    @Email
    @Size(max = 255)
    String email,
    @NotBlank
    @Size(min = 8, max = 255)
    String senha,
    @NotNull
    Perfil perfil,
    @NotNull
    Long paroquiaId
) {}
