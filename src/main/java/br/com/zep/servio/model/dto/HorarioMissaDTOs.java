package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.List;

/** Horário fixo de missa e a escolha de cobertura de cada pastoral (Sprint 4, cartão 1). */
public final class HorarioMissaDTOs {

    private HorarioMissaDTOs() {
    }

    /** {@code diaSemana}: 1 = segunda ... 7 = domingo; sem ele, domingo. */
    public record HorarioMissaRequestDTO(
        @Min(1) @Max(7) Integer diaSemana,
        @NotNull LocalTime hora
    ) {}

    public record HorarioMissaResponseDTO(Long id, Long comunidadeId, int diaSemana, LocalTime hora) {}

    /** Um horário da paróquia e se a pastoral o cobre. */
    public record CoberturaMissaItemDTO(Long horarioMissaId, Long comunidadeId, String comunidade, int diaSemana,
                                        LocalTime hora, boolean cobertura) {}

    public record CoberturaMissaRequestDTO(@NotNull List<@NotNull Long> horarioMissaIds) {}
}
