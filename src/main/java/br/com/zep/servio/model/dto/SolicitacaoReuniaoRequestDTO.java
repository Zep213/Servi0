package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SolicitacaoReuniaoRequestDTO(
    @NotBlank
    @Size(max = 1000)
    String motivo
) {}
