package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotNull;

/** forcar: escala mesmo quem tem impedimento. Só coordenador, padre ou admin pode (o service confere). */
public record EscalarRequestDTO(
    @NotNull
    Long usuarioId,
    boolean forcar
) {}
