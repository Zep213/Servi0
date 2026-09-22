package br.com.zep.servio.mapper;

import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.VagaRequestDTO;
import br.com.zep.servio.model.dto.VagaResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VagaMapper {

    @Mapping(source = "celebracao.id", target = "celebracaoId")
    @Mapping(source = "funcao.id", target = "funcaoId")
    VagaResponseDTO toResponse(Vaga entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    Vaga toEntity(VagaRequestDTO request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    void updateEntity(VagaRequestDTO request, @MappingTarget Vaga entity);
}
