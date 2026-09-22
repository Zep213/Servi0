package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.ComunidadeRequestDTO;
import br.com.zep.servio.model.dto.ComunidadeResponseDTO;
import br.com.zep.servio.service.ComunidadeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/comunidades")
@RequiredArgsConstructor
public class ComunidadeController {

    private final ComunidadeService service;

    @GetMapping
    public List<ComunidadeResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ComunidadeResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<ComunidadeResponseDTO> criar(@Valid @RequestBody ComunidadeRequestDTO request) {
        ComunidadeResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public ComunidadeResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody ComunidadeRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
