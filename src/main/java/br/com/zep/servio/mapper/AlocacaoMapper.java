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

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "dataLimiteResposta", ignore = true)
    Alocacao toEntity(AlocacaoRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "dataLimiteResposta", ignore = true)
    void updateEntity(AlocacaoRequestDTO request, @MappingTarget Alocacao entity);
}
