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

    CompromissoAgenda toEntity(CompromissoAgendaRequestDTO request);

    void updateEntity(CompromissoAgendaRequestDTO request, @MappingTarget CompromissoAgenda entity);
}
