package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.UsuarioFuncaoRequestDTO;
import br.com.zep.servio.model.dto.UsuarioFuncaoResponseDTO;
import br.com.zep.servio.service.UsuarioFuncaoService;
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
@RequestMapping("/api/usuarios-funcoes")
@RequiredArgsConstructor
public class UsuarioFuncaoController {

    private final UsuarioFuncaoService service;

    @GetMapping
    public Page<UsuarioFuncaoResponseDTO> listar(@PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.listar(pageable);
    }

    @GetMapping("/{id}")
    public UsuarioFuncaoResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<UsuarioFuncaoResponseDTO> criar(@Valid @RequestBody UsuarioFuncaoRequestDTO request) {
        UsuarioFuncaoResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public UsuarioFuncaoResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioFuncaoRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
