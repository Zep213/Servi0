package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.EscalaDTOs.EscalaCelebracaoDTO;
import br.com.zep.servio.model.dto.EscalaDTOs.MembroPastoralDTO;
import br.com.zep.servio.service.leitura.LeituraEscalaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Leituras de uma pastoral para as telas de gestão (escala da celebração e membros). */
@RestController
@RequestMapping("/api/pastorais/{pastoralId}")
@RequiredArgsConstructor
public class PastoralLeituraController {

    private final LeituraEscalaService leituraEscalaService;

    @GetMapping("/celebracoes/{celebracaoId}/escala")
    public EscalaCelebracaoDTO escala(@PathVariable Long pastoralId, @PathVariable Long celebracaoId) {
        return leituraEscalaService.escalaDaCelebracao(pastoralId, celebracaoId);
    }

    @GetMapping("/membros")
    public List<MembroPastoralDTO> membros(@PathVariable Long pastoralId) {
        return leituraEscalaService.membros(pastoralId);
    }
}
