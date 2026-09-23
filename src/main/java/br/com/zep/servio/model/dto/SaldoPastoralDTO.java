package br.com.zep.servio.model.dto;

import java.math.BigDecimal;

public record SaldoPastoralDTO(
    BigDecimal totalEntradas,
    BigDecimal totalSaidas,
    BigDecimal saldo
) {}
