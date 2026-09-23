package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.ReuniaoRequestDTO;
import br.com.zep.servio.model.dto.ReuniaoResponseDTO;
import br.com.zep.servio.model.dto.SolicitacaoReuniaoRequestDTO;
import br.com.zep.servio.service.ReuniaoService;
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
@RequestMapping("/api/pastorais/{pastoralId}/reunioes")
@RequiredArgsConstructor
public class ReuniaoController {

    private final ReuniaoService service;

    @GetMapping
    public Page<ReuniaoResponseDTO> listar(@PathVariable Long pastoralId,
                                            @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.listar(pastoralId, pageable);
    }

    @PostMapping
    public ResponseEntity<ReuniaoResponseDTO> marcar(@PathVariable Long pastoralId,
                                                      @Valid @RequestBody ReuniaoRequestDTO request) {
        ReuniaoResponseDTO criada = service.marcar(pastoralId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @PostMapping("/solicitar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void solicitar(@PathVariable Long pastoralId, @Valid @RequestBody SolicitacaoReuniaoRequestDTO request) {
        service.solicitar(pastoralId, request);
    }
}
