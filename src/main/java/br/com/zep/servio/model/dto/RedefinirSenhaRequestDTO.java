package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RedefinirSenhaRequestDTO(
    @NotBlank @Size(max = 64) String token,
    @NotBlank @Size(min = 8, max = 72) String senha      // mesma regra do POST /api/me/senha
) {}
