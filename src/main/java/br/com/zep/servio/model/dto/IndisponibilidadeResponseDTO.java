package br.com.zep.servio.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IndisponibilidadeResponseDTO(
    Long id,
    Long usuarioId,
    LocalDate dataInicio,
    LocalDate dataFim,
    String motivo,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
