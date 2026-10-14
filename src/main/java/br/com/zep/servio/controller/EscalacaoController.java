package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.AlocacaoResponseDTO;
import br.com.zep.servio.model.dto.CandidatoDTO;
import br.com.zep.servio.model.dto.EscalarRequestDTO;
import br.com.zep.servio.model.dto.ResultadoSorteioDTO;
import br.com.zep.servio.service.escalacao.EscalacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class EscalacaoController {

    private final EscalacaoService service;

    @PostMapping("/api/pastorais/{pastoralId}/celebracoes/{celebracaoId}/sortear")
    public ResultadoSorteioDTO sortearCelebracao(@PathVariable Long pastoralId, @PathVariable Long celebracaoId) {
        return service.sortearCelebracao(pastoralId, celebracaoId);
    }

    @PostMapping("/api/vagas/{id}/sortear")
    public ResultadoSorteioDTO sortearVaga(@PathVariable Long id) {
        return service.sortearVaga(id);
    }

    @GetMapping("/api/vagas/{id}/candidatos")
    public List<CandidatoDTO> candidatos(@PathVariable Long id) {
        return service.candidatos(id);
    }

    @PostMapping("/api/vagas/{id}/escalar")
    public ResponseEntity<AlocacaoResponseDTO> escalar(@PathVariable Long id, @Valid @RequestBody EscalarRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.escalar(id, request.usuarioId(), request.forcar()));
    }

    @PostMapping("/api/alocacoes/{id}/substituir")
    public ResponseEntity<AlocacaoResponseDTO> substituir(@PathVariable Long id, @Valid @RequestBody EscalarRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.substituir(id, request.usuarioId(), request.forcar()));
    }

    @PostMapping("/api/alocacoes/{id}/reenviar-convite")
    public AlocacaoResponseDTO reenviarConvite(@PathVariable Long id) {
        return service.reenviarConvite(id);
    }
}
