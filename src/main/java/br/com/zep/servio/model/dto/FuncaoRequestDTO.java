package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record FuncaoRequestDTO(
    @NotBlank
    @Size(max = 255)
    String nome,
    @NotNull
    Long pastoralId
) {}
