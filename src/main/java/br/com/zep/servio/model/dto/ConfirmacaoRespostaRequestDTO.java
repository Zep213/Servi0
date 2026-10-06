package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmacaoRespostaRequestDTO(
    @NotBlank
    @Size(max = 64)
    String token,
    boolean aceitar,
    @Size(max = 500)
    String justificativa
) {}
