package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.dto.FuncaoRequestDTO;
import br.com.zep.servio.model.dto.FuncaoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FuncaoMapper {

    @Mapping(source = "pastoral.id", target = "pastoralId")
    FuncaoResponseDTO toResponse(Funcao entity);

    Funcao toEntity(FuncaoRequestDTO request);

    void updateEntity(FuncaoRequestDTO request, @MappingTarget Funcao entity);
}
