package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.StatusTroca;
import java.time.LocalDateTime;

public record PedidoTrocaResponseDTO(
    Long id,
    Long alocacaoId,
    Long solicitanteId,
    Long destinatarioId,
    StatusTroca status,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
