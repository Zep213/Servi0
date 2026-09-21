package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;

public record UsuarioFuncaoResponseDTO(
    Long id,
    Long usuarioId,
    Long funcaoId,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
