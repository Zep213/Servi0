package br.com.zep.servio.mapper;

import br.com.zep.servio.model.LancamentoFinanceiro;
import br.com.zep.servio.model.dto.LancamentoFinanceiroRequestDTO;
import br.com.zep.servio.model.dto.LancamentoFinanceiroResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LancamentoFinanceiroMapper {

    @Mapping(source = "pastoral.id", target = "pastoralId")
    LancamentoFinanceiroResponseDTO toResponse(LancamentoFinanceiro entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "paroquiaId", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "pastoral", ignore = true)
    LancamentoFinanceiro toEntity(LancamentoFinanceiroRequestDTO request);
}
