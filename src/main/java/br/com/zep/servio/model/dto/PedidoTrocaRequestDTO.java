package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record PedidoTrocaRequestDTO(
    @NotNull
    Long alocacaoId,
    Long destinatarioId
) {}
