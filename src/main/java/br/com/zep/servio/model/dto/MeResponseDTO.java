package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.Perfil;

public record MeResponseDTO(
    Long id,
    String nome,
    String email,
    Perfil perfil,
    Long paroquiaId
) {}
