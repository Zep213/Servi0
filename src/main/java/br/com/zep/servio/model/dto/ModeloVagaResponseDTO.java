package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.TipoCelebracaoModelo;

public record ModeloVagaResponseDTO(
    Long id,
    Long pastoralId,
    Long funcaoId,
    TipoCelebracaoModelo tipoCelebracao,
    Integer quantidade,
    boolean active
) {}
