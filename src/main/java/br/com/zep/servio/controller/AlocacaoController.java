package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.AlocacaoRequestDTO;
import br.com.zep.servio.model.dto.AlocacaoResponseDTO;
import br.com.zep.servio.service.AlocacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/alocacoes")
@RequiredArgsConstructor
public class AlocacaoController {

    private final AlocacaoService service;

    @GetMapping
    public List<AlocacaoResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public AlocacaoResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<AlocacaoResponseDTO> criar(@Valid @RequestBody AlocacaoRequestDTO request) {
        AlocacaoResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public AlocacaoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody AlocacaoRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
