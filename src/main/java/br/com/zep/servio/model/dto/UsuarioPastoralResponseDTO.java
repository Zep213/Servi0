package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.PapelPastoral;

import java.time.LocalDateTime;

public record UsuarioPastoralResponseDTO(
    Long id,
    Long usuarioId,
    Long pastoralId,
    PapelPastoral papel,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
