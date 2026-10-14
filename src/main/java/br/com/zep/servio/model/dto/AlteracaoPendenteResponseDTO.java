package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;

import java.time.LocalDateTime;

public record AlteracaoPendenteResponseDTO(
    Long id,
    Long alocacaoId,
    Long pastoralId,
    Long autorId,
    Long vagaAnteriorId,
    Long usuarioAnteriorId,
    Long vagaNovaId,
    Long usuarioNovoId,
    StatusAlteracaoPendente status,
    LocalDateTime createdAt
) {}
