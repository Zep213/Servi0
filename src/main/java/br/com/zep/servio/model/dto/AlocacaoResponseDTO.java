package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;

public record AlocacaoResponseDTO(
    Long id,
    Long vagaId,
    Long usuarioId,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
