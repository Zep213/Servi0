package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;

public record FuncaoResponseDTO(
    Long id,
    String nome,
    Long pastoralId,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
