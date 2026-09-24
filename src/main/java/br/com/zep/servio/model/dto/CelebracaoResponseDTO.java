package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CelebracaoResponseDTO(
    Long id,
    Long comunidadeId,
    LocalDate data,
    LocalTime hora,
    TipoData tipoData,
    TipoCelebracao tipo,
    String titulo,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
