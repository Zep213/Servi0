package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** E-mail ou telefone, como no login. */
public record RecuperacaoRequestDTO(@NotBlank @Size(max = 255) String login) {}
