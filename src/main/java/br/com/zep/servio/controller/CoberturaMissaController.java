package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.HorarioMissaDTOs.CoberturaMissaItemDTO;
import br.com.zep.servio.model.dto.HorarioMissaDTOs.CoberturaMissaRequestDTO;
import br.com.zep.servio.service.CoberturaMissaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Decisão fina (coordenador/padre/ADMIN, 404 se a pastoral for invisível) no service. */
@RestController
@RequestMapping("/api/pastorais/{pastoralId}/cobertura-missa")
@RequiredArgsConstructor
public class CoberturaMissaController {

    private final CoberturaMissaService service;

    @GetMapping
    public List<CoberturaMissaItemDTO> listar(@PathVariable Long pastoralId) {
        return service.listar(pastoralId);
    }

    @PutMapping
    public List<CoberturaMissaItemDTO> salvar(@PathVariable Long pastoralId,
                                              @Valid @RequestBody CoberturaMissaRequestDTO request) {
        return service.salvar(pastoralId, request.horarioMissaIds());
    }
}
