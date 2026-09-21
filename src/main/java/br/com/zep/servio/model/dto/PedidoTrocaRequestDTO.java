package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record PedidoTrocaRequestDTO(
    @NotNull
    Long alocacaoId,
    @NotNull
    Long solicitanteId,
    Long destinatarioId
) {}
