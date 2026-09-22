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

    Vaga toEntity(VagaRequestDTO request);

    void updateEntity(VagaRequestDTO request, @MappingTarget Vaga entity);
}
