package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.CriarParoquiaRequestDTO;
import br.com.zep.servio.model.dto.ParoquiaPlataformaDTO;
import br.com.zep.servio.model.dto.ResumoPlataformaDTO;
import br.com.zep.servio.service.PlataformaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Só ADMIN: visão da plataforma inteira e assumir/sair de uma paróquia (Parte 2.2). */
@RestController
@RequestMapping("/api/plataforma")
@RequiredArgsConstructor
public class PlataformaController {

    private final PlataformaService service;

    @GetMapping("/paroquias")
    public List<ParoquiaPlataformaDTO> paroquias() {
        return service.paroquias();
    }

    @PostMapping("/paroquias")
    public ResponseEntity<ParoquiaPlataformaDTO> criarParoquia(@Valid @RequestBody CriarParoquiaRequestDTO request) {
        ParoquiaPlataformaDTO criada = service.criarParoquia(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .replacePath("/api/plataforma/paroquias/{id}").buildAndExpand(criada.id()).toUri();
        return ResponseEntity.created(location).body(criada);
    }

    @GetMapping("/resumo")
    public ResumoPlataformaDTO resumo() {
        return service.resumo();
    }

    @PostMapping("/paroquias/{id}/assumir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assumir(@PathVariable Long id) {
        service.assumir(id);
    }

    @PostMapping("/paroquias/sair")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sair() {
        service.sair();
    }
}
