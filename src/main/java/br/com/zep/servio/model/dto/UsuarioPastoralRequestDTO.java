package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.PapelPastoral;
import jakarta.validation.constraints.NotNull;

public record UsuarioPastoralRequestDTO(
    @NotNull
    Long usuarioId,
    @NotNull
    Long pastoralId,
    @NotNull
    PapelPastoral papel
) {}
