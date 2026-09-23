package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.UsuarioPastoralRequestDTO;
import br.com.zep.servio.model.dto.UsuarioPastoralResponseDTO;
import br.com.zep.servio.service.UsuarioPastoralService;
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
@RequestMapping("/api/usuarios-pastorais")
@RequiredArgsConstructor
public class UsuarioPastoralController {

    private final UsuarioPastoralService service;

    @GetMapping
    public Page<UsuarioPastoralResponseDTO> listar(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    public UsuarioPastoralResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<UsuarioPastoralResponseDTO> criar(@Valid @RequestBody UsuarioPastoralRequestDTO request) {
        UsuarioPastoralResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public UsuarioPastoralResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioPastoralRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
