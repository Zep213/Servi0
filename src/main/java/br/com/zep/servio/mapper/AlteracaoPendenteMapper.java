package br.com.zep.servio.mapper;

import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.dto.AlteracaoPendenteResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AlteracaoPendenteMapper {

    @Mapping(source = "alocacao.id", target = "alocacaoId")
    @Mapping(source = "pastoral.id", target = "pastoralId")
    @Mapping(source = "autor.id", target = "autorId")
    @Mapping(source = "vagaAnterior.id", target = "vagaAnteriorId")
    @Mapping(source = "usuarioAnterior.id", target = "usuarioAnteriorId")
    @Mapping(source = "vagaNova.id", target = "vagaNovaId")
    @Mapping(source = "usuarioNovo.id", target = "usuarioNovoId")
    AlteracaoPendenteResponseDTO toResponse(AlteracaoPendente entity);
}
