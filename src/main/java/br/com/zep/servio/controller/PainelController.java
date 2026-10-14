package br.com.zep.servio.controller;

import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.dto.PainelDTO;
import br.com.zep.servio.service.escalacao.PainelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/pastorais/{pastoralId}/painel")
@RequiredArgsConstructor
public class PainelController {

    private final PainelService painelService;
    private final Clock clock;

    /** mes no formato AAAA-MM; sem ele, o mês corrente. */
    @GetMapping
    public PainelDTO painel(@PathVariable Long pastoralId, @RequestParam(required = false) String mes) {
        return painelService.painel(pastoralId, mesOuAtual(mes));
    }

    private YearMonth mesOuAtual(String mes) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now(clock);
        }
        try {
            return YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new RegraNegocioException("Mês inválido, use o formato AAAA-MM");
        }
    }
}
