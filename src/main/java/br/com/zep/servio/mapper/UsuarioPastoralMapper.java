package br.com.zep.servio.mapper;

import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.dto.UsuarioPastoralRequestDTO;
import br.com.zep.servio.model.dto.UsuarioPastoralResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UsuarioPastoralMapper {

    @Mapping(source = "usuario.id", target = "usuarioId")
    @Mapping(source = "pastoral.id", target = "pastoralId")
    UsuarioPastoralResponseDTO toResponse(UsuarioPastoral entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "pastoral", ignore = true)
    UsuarioPastoral toEntity(UsuarioPastoralRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "usuario", ignore = true)
    @Mapping(target = "pastoral", ignore = true)
    void updateEntity(UsuarioPastoralRequestDTO request, @MappingTarget UsuarioPastoral entity);
}
