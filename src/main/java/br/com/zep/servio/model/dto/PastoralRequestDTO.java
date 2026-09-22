package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record PastoralRequestDTO(
    @NotBlank
    @Size(max = 255)
    String nome
) {}
