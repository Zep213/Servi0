package br.com.zep.servio.mapper;

import br.com.zep.servio.model.ModeloVaga;
import br.com.zep.servio.model.dto.ModeloVagaRequestDTO;
import br.com.zep.servio.model.dto.ModeloVagaResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ModeloVagaMapper {

    @Mapping(source = "pastoral.id", target = "pastoralId")
    @Mapping(source = "funcao.id", target = "funcaoId")
    ModeloVagaResponseDTO toResponse(ModeloVaga entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "pastoral", ignore = true)
    @Mapping(target = "funcao", ignore = true)
    ModeloVaga toEntity(ModeloVagaRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "pastoral", ignore = true)
    @Mapping(target = "funcao", ignore = true)
    void updateEntity(ModeloVagaRequestDTO request, @MappingTarget ModeloVaga entity);
}
