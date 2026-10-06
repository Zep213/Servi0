package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.PapelPastoral;

/** Uma pastoral em que o usuário tem papel, para o front montar o menu sem chamar outra rota. */
public record PastoralPapelDTO(Long id, String nome, PapelPastoral papel) {}
