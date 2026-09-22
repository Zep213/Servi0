package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Indisponibilidade;
import br.com.zep.servio.model.dto.IndisponibilidadeRequestDTO;
import br.com.zep.servio.model.dto.IndisponibilidadeResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface IndisponibilidadeMapper {

    @Mapping(source = "usuario.id", target = "usuarioId")
    IndisponibilidadeResponseDTO toResponse(Indisponibilidade entity);

    Indisponibilidade toEntity(IndisponibilidadeRequestDTO request);

    void updateEntity(IndisponibilidadeRequestDTO request, @MappingTarget Indisponibilidade entity);
}
