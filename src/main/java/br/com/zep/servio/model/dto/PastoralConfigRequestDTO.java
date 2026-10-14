package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** Corpo do upsert de configuração de pastoral: pastoralId e chave vêm da URL. */
public record PastoralConfigRequestDTO(
    boolean ativa,
    @NotNull
    Map<String, Object> parametros
) {}
