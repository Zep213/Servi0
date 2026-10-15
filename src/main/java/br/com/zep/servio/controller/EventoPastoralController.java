package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.CelebracaoResponseDTO;
import br.com.zep.servio.model.dto.EventoRequestDTO;
import br.com.zep.servio.service.EventoPastoralService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/** Eventos da pastoral criados pela coordenação. Decisão fina (coordenador/padre/ADMIN, 404/403) no service. */
@RestController
@RequestMapping("/api/pastorais/{pastoralId}/eventos")
@RequiredArgsConstructor
public class EventoPastoralController {

    private final EventoPastoralService service;

    @PostMapping
    public ResponseEntity<CelebracaoResponseDTO> criar(@PathVariable Long pastoralId,
                                                       @Valid @RequestBody EventoRequestDTO request) {
        CelebracaoResponseDTO criado = service.criar(pastoralId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @PutMapping("/{celebracaoId}")
    public CelebracaoResponseDTO atualizar(@PathVariable Long pastoralId, @PathVariable Long celebracaoId,
                                           @Valid @RequestBody EventoRequestDTO request) {
        return service.atualizar(pastoralId, celebracaoId, request);
    }

    @DeleteMapping("/{celebracaoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long pastoralId, @PathVariable Long celebracaoId) {
        service.desativar(pastoralId, celebracaoId);
    }
}
