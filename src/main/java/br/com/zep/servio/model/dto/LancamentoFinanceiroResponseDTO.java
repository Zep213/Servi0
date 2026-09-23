package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.TipoLancamento;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoFinanceiroResponseDTO(
    Long id,
    Long pastoralId,
    TipoLancamento tipo,
    BigDecimal valor,
    String descricao,
    LocalDate dataLancamento
) {}
