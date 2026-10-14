package br.com.zep.servio.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** ADMIN cria a paróquia junto com o primeiro PADRE dela, com uma senha temporária. */
public record CriarParoquiaRequestDTO(
    @NotBlank @Size(max = 255) String nome,
    @NotBlank @Email @Size(max = 255) String emailContato,
    @NotBlank @Size(max = 255) String padreNome,
    @NotBlank @Email @Size(max = 255) String padreEmail,
    @NotBlank @Size(min = 8, max = 72) String padreSenha
) {}
