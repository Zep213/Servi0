package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.PastoralConfigRequestDTO;
import br.com.zep.servio.model.dto.PastoralConfigResponseDTO;
import br.com.zep.servio.service.escalacao.PastoralConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pastorais/{pastoralId}/config")
@RequiredArgsConstructor
public class PastoralConfigController {

    private final PastoralConfigService service;

    @GetMapping
    public List<PastoralConfigResponseDTO> listar(@PathVariable Long pastoralId) {
        return service.efetivas(pastoralId);
    }

    /** Decisão fina (COORDENADOR/PADRE/ADMIN, 404 se a pastoral for invisível) no service. */
    @PutMapping("/{chave}")
    public PastoralConfigResponseDTO salvar(@PathVariable Long pastoralId, @PathVariable String chave,
                                             @Valid @RequestBody PastoralConfigRequestDTO request) {
        return service.salvar(pastoralId, chave, request);
    }
}
