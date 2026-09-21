package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record UsuarioFuncaoRequestDTO(
    @NotNull
    Long usuarioId,
    @NotNull
    Long funcaoId
) {}
