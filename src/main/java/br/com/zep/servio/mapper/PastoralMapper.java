package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.dto.PastoralRequestDTO;
import br.com.zep.servio.model.dto.PastoralResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PastoralMapper {

    PastoralResponseDTO toResponse(Pastoral entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    Pastoral toEntity(PastoralRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntity(PastoralRequestDTO request, @MappingTarget Pastoral entity);
}
