package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record VagaRequestDTO(
    @NotNull
    Long celebracaoId,
    @NotNull
    Long funcaoId,
    @NotNull
    @Positive
    Integer quantidade
) {}
