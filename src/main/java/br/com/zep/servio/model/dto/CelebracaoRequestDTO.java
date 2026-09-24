package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.model.enumerated.TipoData;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;

public record CelebracaoRequestDTO(
    @NotNull
    Long comunidadeId,
    @NotNull
    LocalDate data,
    @NotNull
    LocalTime hora,
    TipoData tipoData,
    TipoCelebracao tipo,
    @Size(max = 120)
    String titulo
) {}
