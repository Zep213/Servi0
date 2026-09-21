package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;

public record VagaResponseDTO(
    Long id,
    Long celebracaoId,
    Long funcaoId,
    Integer quantidade,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
