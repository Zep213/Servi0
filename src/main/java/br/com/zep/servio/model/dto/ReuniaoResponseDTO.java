package br.com.zep.servio.model.dto;

import java.time.LocalDateTime;

public record ReuniaoResponseDTO(
    Long id,
    Long pastoralId,
    String titulo,
    LocalDateTime dataHora,
    String local
) {}
