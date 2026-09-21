package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record AlocacaoRequestDTO(
    @NotNull
    Long vagaId,
    @NotNull
    Long usuarioId
) {}
