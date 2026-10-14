package br.com.zep.servio.model.dto;

import java.util.Map;

/** Configuração efetiva de uma chave (regra de elegibilidade ou PRAZO_RESPOSTA) para uma pastoral. */
public record PastoralConfigResponseDTO(
    String chave,
    boolean ativa,
    Map<String, Object> parametros,
    boolean configuradaPelaPastoral
) {}
