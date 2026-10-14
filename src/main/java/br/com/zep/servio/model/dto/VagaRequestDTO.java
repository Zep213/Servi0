package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;
import java.time.LocalTime;

public record VagaRequestDTO(
    @NotNull
    Long celebracaoId,
    @NotNull
    Long funcaoId,
    @Positive
    Integer quantidade,
    LocalTime horarioChegada,
    @Size(max = 500)
    String observacao
) {}
