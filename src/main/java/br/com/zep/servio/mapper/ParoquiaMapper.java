package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.dto.ParoquiaRequestDTO;
import br.com.zep.servio.model.dto.ParoquiaResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ParoquiaMapper {

    ParoquiaResponseDTO toResponse(Paroquia entity);

    Paroquia toEntity(ParoquiaRequestDTO request);

    void updateEntity(ParoquiaRequestDTO request, @MappingTarget Paroquia entity);
}
