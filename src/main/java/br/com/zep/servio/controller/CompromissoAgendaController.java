package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.CompromissoAgendaRequestDTO;
import br.com.zep.servio.model.dto.CompromissoAgendaResponseDTO;
import br.com.zep.servio.service.CompromissoAgendaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/compromissos-agenda")
@RequiredArgsConstructor
public class CompromissoAgendaController {

    private final CompromissoAgendaService service;

    @GetMapping
    public Page<CompromissoAgendaResponseDTO> listar(@PageableDefault(size = 20, sort = "data") Pageable pageable) {
        return service.listar(pageable);
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
