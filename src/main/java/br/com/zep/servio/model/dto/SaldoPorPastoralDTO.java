package br.com.zep.servio.model.dto;

import java.math.BigDecimal;

public record SaldoPorPastoralDTO(
    Long pastoralId,
    String pastoralNome,
    BigDecimal totalEntradas,
    BigDecimal totalSaidas,
    BigDecimal saldo
) {}
