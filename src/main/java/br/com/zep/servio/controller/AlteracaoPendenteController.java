package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.AlteracaoPendenteResponseDTO;
import br.com.zep.servio.service.AlteracaoPendenteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alteracoes-pendentes")
@RequiredArgsConstructor
public class AlteracaoPendenteController {

    private final AlteracaoPendenteService service;

    @GetMapping
    public Page<AlteracaoPendenteResponseDTO> listar(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.listar(pageable);
    }

    @PostMapping("/{id}/confirmar")
    public AlteracaoPendenteResponseDTO confirmar(@PathVariable Long id) {
        return service.confirmar(id);
    }

    @PostMapping("/{id}/desfazer")
    public AlteracaoPendenteResponseDTO desfazer(@PathVariable Long id) {
        return service.desfazer(id);
    }
}
