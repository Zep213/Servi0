package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.Perfil;

import java.util.List;

public record MeResponseDTO(
    Long id,
    String nome,
    String email,
    Perfil perfil,
    Long paroquiaId,
    List<PastoralPapelDTO> pastorais
) {}
