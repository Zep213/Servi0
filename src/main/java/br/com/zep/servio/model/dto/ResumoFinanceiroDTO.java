package br.com.zep.servio.model.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResumoFinanceiroDTO(
    BigDecimal totalEntradas,
    BigDecimal totalSaidas,
    BigDecimal saldo,
    List<SaldoPorPastoralDTO> porPastoral
) {}
