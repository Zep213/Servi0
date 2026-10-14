package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.TipoCelebracaoModelo;
import jakarta.validation.constraints.*;

public record ModeloVagaRequestDTO(
    @NotNull
    Long funcaoId,
    @NotNull
    TipoCelebracaoModelo tipoCelebracao,
    @NotNull
    @Positive
    Integer quantidade
) {}
