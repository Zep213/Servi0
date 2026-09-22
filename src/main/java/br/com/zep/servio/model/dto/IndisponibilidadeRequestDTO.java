package br.com.zep.servio.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record IndisponibilidadeRequestDTO(
    Long usuarioId,
    @NotNull
    LocalDate dataInicio,
    @NotNull
    LocalDate dataFim,
    @Size(max = 500)
    String motivo
) {

    @AssertTrue(message = "dataFim deve ser igual ou posterior a dataInicio")
    @JsonIgnore
    public boolean isPeriodoValido() {
        return dataInicio == null || dataFim == null || !dataFim.isBefore(dataInicio);
    }
}
