package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.PastoralRequestDTO;
import br.com.zep.servio.model.dto.PastoralResponseDTO;
import br.com.zep.servio.service.PastoralService;
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
@RequestMapping("/api/pastorais")
@RequiredArgsConstructor
public class PastoralController {

    private final PastoralService service;

    @GetMapping
    public Page<PastoralResponseDTO> listar(@PageableDefault(size = 20, sort = "nome") Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    public PastoralResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<PastoralResponseDTO> criar(@Valid @RequestBody PastoralRequestDTO request) {
        PastoralResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public PastoralResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody PastoralRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
