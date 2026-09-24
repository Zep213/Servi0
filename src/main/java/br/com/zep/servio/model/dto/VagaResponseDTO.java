package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record VagaResponseDTO(
    Long id,
    Long celebracaoId,
    Long funcaoId,
    Integer quantidade,
    LocalTime horarioChegada,
    String observacao,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
