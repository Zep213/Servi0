package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.MembroCriadoDTO;
import br.com.zep.servio.model.dto.MembroNovoRequestDTO;
import br.com.zep.servio.service.conta.CadastroMembroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cadastro de membro sem senha numa operação só, e reenvio do link de ativação. */
@RestController
@RequestMapping("/api/pastorais/{pastoralId}/membros")
@RequiredArgsConstructor
public class MembroCadastroController {

    private final CadastroMembroService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MembroCriadoDTO cadastrar(@PathVariable Long pastoralId, @Valid @RequestBody MembroNovoRequestDTO request) {
        return service.cadastrar(pastoralId, request);
    }

    @PostMapping("/{usuarioId}/reenviar-ativacao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reenviarAtivacao(@PathVariable Long pastoralId, @PathVariable Long usuarioId) {
        service.reenviarAtivacao(pastoralId, usuarioId);
    }
}
