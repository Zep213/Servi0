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

    @Mapping(source = "paroquia.id", target = "paroquiaId")
    ComunidadeResponseDTO toResponse(Comunidade entity);

    Comunidade toEntity(ComunidadeRequestDTO request);

    void updateEntity(ComunidadeRequestDTO request, @MappingTarget Comunidade entity);
}
