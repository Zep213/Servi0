package br.com.zep.servio.mapper;

import br.com.zep.servio.model.PedidoTroca;
import br.com.zep.servio.model.dto.PedidoTrocaRequestDTO;
import br.com.zep.servio.model.dto.PedidoTrocaResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PedidoTrocaMapper {

    @Mapping(source = "alocacao.id", target = "alocacaoId")
    @Mapping(source = "solicitante.id", target = "solicitanteId")
    @Mapping(source = "destinatario.id", target = "destinatarioId")
    PedidoTrocaResponseDTO toResponse(PedidoTroca entity);

    PedidoTroca toEntity(PedidoTrocaRequestDTO request);

    void updateEntity(PedidoTrocaRequestDTO request, @MappingTarget PedidoTroca entity);
}
