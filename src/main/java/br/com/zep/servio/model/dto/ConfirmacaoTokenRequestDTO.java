package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmacaoTokenRequestDTO(
    @NotBlank
    @Size(max = 64)
    String token
) {}
