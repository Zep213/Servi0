package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.IndisponibilidadeRequestDTO;
import br.com.zep.servio.model.dto.IndisponibilidadeResponseDTO;
import br.com.zep.servio.service.IndisponibilidadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/indisponibilidades")
@RequiredArgsConstructor
public class IndisponibilidadeController {

    private final IndisponibilidadeService service;

    @GetMapping
    public List<IndisponibilidadeResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public IndisponibilidadeResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<IndisponibilidadeResponseDTO> criar(@Valid @RequestBody IndisponibilidadeRequestDTO request) {
        IndisponibilidadeResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public IndisponibilidadeResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody IndisponibilidadeRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
