package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record ComunidadeRequestDTO(
    @NotBlank
    @Size(max = 255)
    String nome
) {}
