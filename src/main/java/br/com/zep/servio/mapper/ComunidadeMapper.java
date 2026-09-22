package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.dto.ComunidadeRequestDTO;
import br.com.zep.servio.model.dto.ComunidadeResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ComunidadeMapper {

    ComunidadeResponseDTO toResponse(Comunidade entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    Comunidade toEntity(ComunidadeRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntity(ComunidadeRequestDTO request, @MappingTarget Comunidade entity);
}
