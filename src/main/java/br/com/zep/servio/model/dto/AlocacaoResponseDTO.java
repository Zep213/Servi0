package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.OrigemAlocacao;
import br.com.zep.servio.model.enumerated.StatusConvite;

import java.time.LocalDateTime;

public record AlocacaoResponseDTO(
    Long id,
    Long vagaId,
    Long usuarioId,
    StatusConvite status,
    LocalDateTime dataLimiteResposta,
    OrigemAlocacao origem,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
