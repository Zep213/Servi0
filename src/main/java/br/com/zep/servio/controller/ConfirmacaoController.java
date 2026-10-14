package br.com.zep.servio.controller;

import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.dto.ConfirmacaoDetalhesDTO;
import br.com.zep.servio.model.dto.ConfirmacaoRespostaDTO;
import br.com.zep.servio.model.dto.ConfirmacaoRespostaRequestDTO;
import br.com.zep.servio.model.dto.ConfirmacaoTokenRequestDTO;
import br.com.zep.servio.service.escalacao.ConfirmacaoPublicaService;
import br.com.zep.servio.service.escalacao.LimiteConfirmacoes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Página pública do convite (/convite#token). Sem login; o token é a credencial. */
@RestController
@RequestMapping("/api/confirmacoes")
@RequiredArgsConstructor
public class ConfirmacaoController {

    private final ConfirmacaoPublicaService service;
    private final LimiteConfirmacoes limite;

    @PostMapping("/detalhes")
    public ConfirmacaoDetalhesDTO detalhes(@Valid @RequestBody ConfirmacaoTokenRequestDTO request,
                                           HttpServletRequest http) {
        exigirDentroDoLimite(http);
        return service.detalhes(request.token());
    }

    @PostMapping("/responder")
    public ConfirmacaoRespostaDTO responder(@Valid @RequestBody ConfirmacaoRespostaRequestDTO request,
                                            HttpServletRequest http) {
        exigirDentroDoLimite(http);
        return service.responder(request.token(), request.aceitar(), request.justificativa());
    }

    private void exigirDentroDoLimite(HttpServletRequest http) {
        if (!limite.permitir(http.getRemoteAddr())) {
            throw new ServioException("Muitas tentativas. Aguarde um minuto e tente de novo.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
