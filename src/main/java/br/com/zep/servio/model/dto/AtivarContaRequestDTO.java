package br.com.zep.servio.model.dto;

import br.com.zep.servio.util.validacao.SenhaBcrypt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AtivarContaRequestDTO(
    @NotBlank @Size(max = 64) String token,
    @NotBlank @SenhaBcrypt String senha      // mesma regra do POST /api/me/senha
) {}
