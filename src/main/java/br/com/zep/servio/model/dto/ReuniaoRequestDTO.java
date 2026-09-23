package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ReuniaoRequestDTO(
    @NotBlank
    @Size(max = 255)
    String titulo,
    @NotNull
    LocalDateTime dataHora,
    @Size(max = 255)
    String local
) {}
