package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.*;

public record ParoquiaRequestDTO(
    @NotBlank
    @Size(max = 255)
    String nome,
    @NotBlank
    @Email
    @Size(max = 255)
    String emailContato,
    @Size(max = 50)
    String telefone
) {}
