package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.ModeloVagaRequestDTO;
import br.com.zep.servio.model.dto.ModeloVagaResponseDTO;
import br.com.zep.servio.service.ModeloVagaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/** Só o coordenador da pastoral (ou padre/admin) gerencia modelos de vaga (Parte 3.3). */
@RestController
@RequestMapping("/api/pastorais/{pastoralId}/modelos-vaga")
@RequiredArgsConstructor
@PreAuthorize("@pastorais.temPapel(#pastoralId, 'COORDENADOR')")
public class ModeloVagaController {

    private final ModeloVagaService service;

    @GetMapping
    public List<ModeloVagaResponseDTO> listar(@PathVariable Long pastoralId) {
        return service.listar(pastoralId);
    }

    @PostMapping
    public ResponseEntity<ModeloVagaResponseDTO> criar(@PathVariable Long pastoralId,
                                                         @Valid @RequestBody ModeloVagaRequestDTO request) {
        ModeloVagaResponseDTO criado = service.criar(pastoralId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ModeloVagaResponseDTO atualizar(@PathVariable Long pastoralId, @PathVariable Long id,
                                            @Valid @RequestBody ModeloVagaRequestDTO request) {
        return service.atualizar(pastoralId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long pastoralId, @PathVariable Long id) {
        service.desativar(pastoralId, id);
    }

    @PostMapping("/aplicar-futuras")
    public Map<String, Integer> aplicarFuturas(@PathVariable Long pastoralId) {
        return Map.of("vagasCriadas", service.aplicarFuturas(pastoralId));
    }
}
