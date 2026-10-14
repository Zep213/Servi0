package br.com.zep.servio.model.dto;

import br.com.zep.servio.model.enumerated.TipoLancamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LancamentoFinanceiroRequestDTO(
    @NotNull
    TipoLancamento tipo,
    @NotNull
    @Positive
    BigDecimal valor,
    @NotBlank
    @Size(max = 255)
    String descricao,
    @NotNull
    LocalDate dataLancamento
) {}
