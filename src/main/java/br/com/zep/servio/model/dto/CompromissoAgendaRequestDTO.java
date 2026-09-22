package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;

public record CompromissoAgendaRequestDTO(
    @NotBlank
    @Size(max = 255)
    String titulo,
    @NotNull
    LocalDate data,
    LocalTime hora,
    @Size(max = 100)
    String tipo,
    Long comunidadeId,
    Boolean privado
) {}
