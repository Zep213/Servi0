package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.LancamentoFinanceiroRequestDTO;
import br.com.zep.servio.model.dto.LancamentoFinanceiroResponseDTO;
import br.com.zep.servio.model.dto.SaldoPastoralDTO;
import br.com.zep.servio.service.LancamentoFinanceiroService;
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
@RequestMapping("/api/pastorais/{pastoralId}/financeiro")
@RequiredArgsConstructor
public class LancamentoFinanceiroController {

    private final LancamentoFinanceiroService service;

    @GetMapping
    public Page<LancamentoFinanceiroResponseDTO> listar(@PathVariable Long pastoralId,
                                                          @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.listar(pastoralId, pageable);
    }

    @GetMapping("/saldo")
    public SaldoPastoralDTO saldo(@PathVariable Long pastoralId) {
        return service.saldo(pastoralId);
    }

    @PostMapping
    public ResponseEntity<LancamentoFinanceiroResponseDTO> criar(@PathVariable Long pastoralId,
                                                                  @Valid @RequestBody LancamentoFinanceiroRequestDTO request) {
        LancamentoFinanceiroResponseDTO criado = service.criar(pastoralId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(location).body(criado);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desativar(@PathVariable Long pastoralId, @PathVariable Long id) {
        service.desativar(pastoralId, id);
    }
}
