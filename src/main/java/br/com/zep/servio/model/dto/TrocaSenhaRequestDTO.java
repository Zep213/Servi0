package br.com.zep.servio.model.dto;

import br.com.zep.servio.util.validacao.SenhaBcrypt;
import jakarta.validation.constraints.NotBlank;

public record TrocaSenhaRequestDTO(
    @NotBlank String senhaAtual,
    @NotBlank @SenhaBcrypt String senhaNova
) {}
