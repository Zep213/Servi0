package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Reuniao;
import br.com.zep.servio.model.dto.ReuniaoRequestDTO;
import br.com.zep.servio.model.dto.ReuniaoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ReuniaoMapper {

    @Mapping(source = "pastoral.id", target = "pastoralId")
    ReuniaoResponseDTO toResponse(Reuniao entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "pastoral", ignore = true)
    Reuniao toEntity(ReuniaoRequestDTO request);
}
