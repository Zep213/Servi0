package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.TipoData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Evento criado ou editado pela coordenação da pastoral. O título é obrigatório (na rota do padre é opcional). */
public record EventoRequestDTO(
    @NotNull Long comunidadeId,
    @NotNull LocalDate data,
    @NotNull LocalTime hora,
    @NotBlank @Size(max = 120) String titulo,
    TipoData tipoData
) {}
