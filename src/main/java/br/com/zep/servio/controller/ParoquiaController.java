package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.ParoquiaRequestDTO;
import br.com.zep.servio.model.dto.ParoquiaResponseDTO;
import br.com.zep.servio.service.ParoquiaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/paroquias")
@RequiredArgsConstructor
public class ParoquiaController {

    private final ParoquiaService service;

    @GetMapping("/minha")
    public ParoquiaResponseDTO minha() {
        return service.minha();
    }

    @PutMapping("/minha")
    public ParoquiaResponseDTO atualizar(@Valid @RequestBody ParoquiaRequestDTO request) {
        return service.atualizarMinha(request);
    }
}
