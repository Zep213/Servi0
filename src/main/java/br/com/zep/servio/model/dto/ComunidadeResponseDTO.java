package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;

public record ComunidadeResponseDTO(
    Long id,
    String nome,
    Long paroquiaId,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
