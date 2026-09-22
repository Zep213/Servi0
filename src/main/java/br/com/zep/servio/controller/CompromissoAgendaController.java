package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.CompromissoAgendaRequestDTO;
import br.com.zep.servio.model.dto.CompromissoAgendaResponseDTO;
import br.com.zep.servio.service.CompromissoAgendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/compromissos-agenda")
@RequiredArgsConstructor
public class CompromissoAgendaController {

    private final CompromissoAgendaService service;

    @GetMapping
    public List<CompromissoAgendaResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public CompromissoAgendaResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<CompromissoAgendaResponseDTO> criar(@Valid @RequestBody CompromissoAgendaRequestDTO request) {
        CompromissoAgendaResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public CompromissoAgendaResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody CompromissoAgendaRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
