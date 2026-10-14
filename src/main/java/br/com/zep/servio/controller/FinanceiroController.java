package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.ResumoFinanceiroDTO;
import br.com.zep.servio.service.LancamentoFinanceiroService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Dashboard financeiro consolidado da paróquia (Parte 4) — só PADRE/ADMIN, gate no SecurityConfig. */
@RestController
@RequestMapping("/api/financeiro")
@RequiredArgsConstructor
public class FinanceiroController {

    private final LancamentoFinanceiroService service;

    @GetMapping("/resumo")
    public ResumoFinanceiroDTO resumo(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.resumoConsolidado(de, ate);
    }
}
