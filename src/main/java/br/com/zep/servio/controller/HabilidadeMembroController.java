package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.HabilidadeDTO;
import br.com.zep.servio.model.dto.HabilidadesRequestDTO;
import br.com.zep.servio.service.HabilidadeMembroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Habilidades de um membro da pastoral (tela "Detalhe do membro"). */
@RestController
@RequestMapping("/api/pastorais/{pastoralId}/membros/{usuarioId}/habilidades")
@RequiredArgsConstructor
public class HabilidadeMembroController {

    private final HabilidadeMembroService service;

    @GetMapping
    public List<HabilidadeDTO> listar(@PathVariable Long pastoralId, @PathVariable Long usuarioId) {
        return service.listar(pastoralId, usuarioId);
    }

    @PutMapping
    public List<HabilidadeDTO> substituir(@PathVariable Long pastoralId, @PathVariable Long usuarioId,
                                          @Valid @RequestBody HabilidadesRequestDTO request) {
        return service.substituir(pastoralId, usuarioId, request.funcaoIds());
    }
}
