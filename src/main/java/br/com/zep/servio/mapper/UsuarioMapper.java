package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.UsuarioRequestDTO;
import br.com.zep.servio.model.dto.UsuarioResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UsuarioMapper {

    @Mapping(source = "paroquia.id", target = "paroquiaId")
    UsuarioResponseDTO toResponse(Usuario entity);

    Usuario toEntity(UsuarioRequestDTO request);

    void updateEntity(UsuarioRequestDTO request, @MappingTarget Usuario entity);
}
