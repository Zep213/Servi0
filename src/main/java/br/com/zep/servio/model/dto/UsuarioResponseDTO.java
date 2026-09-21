package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.Perfil;
import java.time.LocalDateTime;

public record UsuarioResponseDTO(
    Long id,
    String nome,
    String email,
    Perfil perfil,
    Long paroquiaId,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
