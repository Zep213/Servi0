package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.Aparencia;

/** PUT /api/me: todos os campos são opcionais, só muda o que vier. */
public record MeUpdateDTO(String telefone, Aparencia aparencia, Boolean pularTelefone) {}
