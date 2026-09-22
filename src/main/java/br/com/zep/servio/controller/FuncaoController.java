package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.FuncaoRequestDTO;
import br.com.zep.servio.model.dto.FuncaoResponseDTO;
import br.com.zep.servio.service.FuncaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/funcoes")
@RequiredArgsConstructor
public class FuncaoController {

    private final FuncaoService service;

    @GetMapping
    public List<FuncaoResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public FuncaoResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<FuncaoResponseDTO> criar(@Valid @RequestBody FuncaoRequestDTO request) {
        FuncaoResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public FuncaoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody FuncaoRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
