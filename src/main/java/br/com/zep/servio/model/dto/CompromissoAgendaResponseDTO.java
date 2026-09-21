package br.com.zep.servio.model.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CompromissoAgendaResponseDTO(
    Long id,
    Long padreId,
    String titulo,
    LocalDate data,
    LocalTime hora,
    String tipo,
    Long comunidadeId,
    Boolean privado,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
