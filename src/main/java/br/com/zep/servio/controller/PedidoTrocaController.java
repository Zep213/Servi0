package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.PedidoTrocaRequestDTO;
import br.com.zep.servio.model.dto.PedidoTrocaResponseDTO;
import br.com.zep.servio.service.PedidoTrocaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/pedidos-troca")
@RequiredArgsConstructor
public class PedidoTrocaController {

    private final PedidoTrocaService service;

    @GetMapping
    public List<PedidoTrocaResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public PedidoTrocaResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<PedidoTrocaResponseDTO> criar(@Valid @RequestBody PedidoTrocaRequestDTO request) {
        PedidoTrocaResponseDTO criado = service.criar(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public PedidoTrocaResponseDTO atualizar(@PathVariable Long id, @Valid @RequestBody PedidoTrocaRequestDTO request) {
        return service.atualizar(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long id) {
        service.desativar(id);
    }
}
