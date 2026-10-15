package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.Aparencia;
import jakarta.validation.constraints.Size;

/** PUT /api/me: todos os campos são opcionais, só muda o que vier. */
public record MeUpdateDTO(@Size(max = 40) String telefone, Aparencia aparencia, Boolean pularTelefone) {}
