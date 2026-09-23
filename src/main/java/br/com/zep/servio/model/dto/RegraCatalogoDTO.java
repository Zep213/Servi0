package br.com.zep.servio.model.dto;

import java.util.Map;

public record RegraCatalogoDTO(
    String codigo,
    String descricao,
    Map<String, Object> padroes
) {}
