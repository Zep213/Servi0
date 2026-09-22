package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TrocaSenhaRequestDTO(
    @NotBlank String senhaAtual,
    @NotBlank @Size(min = 8, max = 72) String senhaNova
) {}
