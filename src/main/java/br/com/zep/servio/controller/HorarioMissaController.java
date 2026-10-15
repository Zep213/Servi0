package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.HorarioMissaDTOs.HorarioMissaRequestDTO;
import br.com.zep.servio.model.dto.HorarioMissaDTOs.HorarioMissaResponseDTO;
import br.com.zep.servio.service.HorarioMissaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Horários fixos de missa da comunidade. Escrita só PADRE/ADMIN (a rota está em CADASTROS e o service confere). */
@RestController
@RequestMapping("/api/comunidades/{comunidadeId}/horarios-missa")
@RequiredArgsConstructor
public class HorarioMissaController {

    private final HorarioMissaService service;

    @GetMapping
    public List<HorarioMissaResponseDTO> listar(@PathVariable Long comunidadeId) {
        return service.listar(comunidadeId);
    }

    @PostMapping
    public ResponseEntity<HorarioMissaResponseDTO> criar(@PathVariable Long comunidadeId,
                                                         @Valid @RequestBody HorarioMissaRequestDTO request) {
        HorarioMissaResponseDTO criado = service.criar(comunidadeId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{id}")
    public HorarioMissaResponseDTO atualizar(@PathVariable Long comunidadeId, @PathVariable Long id,
                                             @Valid @RequestBody HorarioMissaRequestDTO request) {
        return service.atualizar(comunidadeId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long comunidadeId, @PathVariable Long id) {
        service.desativar(comunidadeId, id);
    }
}
