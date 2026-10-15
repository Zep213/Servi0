package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.Aparencia;
import br.com.zep.servio.model.enumerated.Perfil;

import java.util.List;

public record MeResponseDTO(
    Long id,
    String nome,
    String email,
    Perfil perfil,
    Long paroquiaId,
    String telefone,
    Aparencia aparencia,
    boolean pedirTelefone,
    String fotoUrl,
    List<PastoralPapelDTO> pastorais
) {}
