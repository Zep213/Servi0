package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.PapelPastoral;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cadastro de membro sem senha: o papel é opcional e, sem ele, a pessoa entra como MEMBRO. */
public record MembroNovoRequestDTO(
    @NotBlank @Size(max = 255) String nome,
    @NotBlank @Email @Size(max = 255) String email,
    PapelPastoral papel
) {}
