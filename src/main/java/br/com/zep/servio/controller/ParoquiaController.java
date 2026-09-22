package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.ParoquiaRequestDTO;
import br.com.zep.servio.model.dto.ParoquiaResponseDTO;
import br.com.zep.servio.service.ParoquiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/paroquias")
@RequiredArgsConstructor
public class ParoquiaController {

    private final ParoquiaService service;

    @GetMapping
    public List<ParoquiaResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ParoquiaResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<ParoquiaResponseDTO> criar(@Valid @RequestBody ParoquiaRequestDTO request) {
        ParoquiaResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ParoquiaResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody ParoquiaRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
