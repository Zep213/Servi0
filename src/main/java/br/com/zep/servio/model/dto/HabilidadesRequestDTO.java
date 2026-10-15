package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** O conjunto completo de habilidades da pessoa na pastoral: o que não estiver na lista é desmarcado. */
public record HabilidadesRequestDTO(@NotNull @Size(max = 200) List<Long> funcaoIds) {}
