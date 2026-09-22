package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.dto.AlocacaoRequestDTO;
import br.com.zep.servio.model.dto.AlocacaoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AlocacaoMapper {

    @Mapping(source = "vaga.id", target = "vagaId")
    @Mapping(source = "usuario.id", target = "usuarioId")
    AlocacaoResponseDTO toResponse(Alocacao entity);

    Alocacao toEntity(AlocacaoRequestDTO request);

    void updateEntity(AlocacaoRequestDTO request, @MappingTarget Alocacao entity);
}
