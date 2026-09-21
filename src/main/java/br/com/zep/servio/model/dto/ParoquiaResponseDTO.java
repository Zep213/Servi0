package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;

public record ParoquiaResponseDTO(
    Long id,
    String nome,
    String emailContato,
    String telefone,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
