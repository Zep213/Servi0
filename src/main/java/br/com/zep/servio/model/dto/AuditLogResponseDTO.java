package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;

public record AuditLogResponseDTO(
    Long id,
    String tipoEvento,
    Long referenciaId,
    Long usuarioId,
    Long paroquiaId,
    String detalhe,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
