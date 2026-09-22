package br.com.zep.servio.mapper;

import br.com.zep.servio.model.CompromissoAgenda;
import br.com.zep.servio.model.dto.CompromissoAgendaRequestDTO;
import br.com.zep.servio.model.dto.CompromissoAgendaResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CompromissoAgendaMapper {

    @Mapping(source = "padre.id", target = "padreId")
    @Mapping(source = "comunidade.id", target = "comunidadeId")
    CompromissoAgendaResponseDTO toResponse(CompromissoAgenda entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    CompromissoAgenda toEntity(CompromissoAgendaRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntity(CompromissoAgendaRequestDTO request, @MappingTarget CompromissoAgenda entity);
}
