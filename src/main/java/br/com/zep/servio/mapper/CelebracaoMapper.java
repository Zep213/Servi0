package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.dto.CelebracaoRequestDTO;
import br.com.zep.servio.model.dto.CelebracaoResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CelebracaoMapper {

    @Mapping(source = "comunidade.id", target = "comunidadeId")
    CelebracaoResponseDTO toResponse(Celebracao entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "tipoData", defaultValue = "NORMAL")
    Celebracao toEntity(CelebracaoRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "tipoData", defaultValue = "NORMAL")
    void updateEntity(CelebracaoRequestDTO request, @MappingTarget Celebracao entity);
}
