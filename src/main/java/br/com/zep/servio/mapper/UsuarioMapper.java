package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.UsuarioRequestDTO;
import br.com.zep.servio.model.dto.UsuarioResponseDTO;
import br.com.zep.servio.model.dto.UsuarioUpdateDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UsuarioMapper {

    UsuarioResponseDTO toResponse(Usuario entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "senha", ignore = true)          // a senha é gravada com hash no service
    Usuario toEntity(UsuarioRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "senha", ignore = true)
    void updateEntity(UsuarioUpdateDTO request, @MappingTarget Usuario entity);
}
