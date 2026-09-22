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

    @Mapping(source = "paroquia.id", target = "paroquiaId")
    PastoralResponseDTO toResponse(Pastoral entity);

    Pastoral toEntity(PastoralRequestDTO request);

    void updateEntity(PastoralRequestDTO request, @MappingTarget Pastoral entity);
}
