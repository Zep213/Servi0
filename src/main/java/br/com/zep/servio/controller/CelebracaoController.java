package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.CelebracaoRequestDTO;
import br.com.zep.servio.model.dto.CelebracaoResponseDTO;
import br.com.zep.servio.service.CelebracaoService;
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
@RequestMapping("/api/celebracoes")
@RequiredArgsConstructor
public class CelebracaoController {

    private final CelebracaoService service;

    @GetMapping
    public Page<CelebracaoResponseDTO> listar(@PageableDefault(size = 20, sort = "data") Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    public CelebracaoResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<CelebracaoResponseDTO> criar(@Valid @RequestBody CelebracaoRequestDTO request) {
        CelebracaoResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public CelebracaoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody CelebracaoRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
