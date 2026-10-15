package br.com.zep.servio.controller;

import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.dto.AtivacaoDetalhesDTO;
import br.com.zep.servio.model.dto.AtivarContaRequestDTO;
import br.com.zep.servio.model.dto.ConfirmacaoTokenRequestDTO;
import br.com.zep.servio.service.conta.AtivacaoContaService;
import br.com.zep.servio.service.conta.LimiteContaPublica;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Páginas públicas de conta (/ativar#token). Sem login; o token é a credencial. */
@RestController
@RequestMapping("/api/contas")
@RequiredArgsConstructor
public class ContaPublicaController {

    private final AtivacaoContaService ativacaoContaService;
    private final LimiteContaPublica limite;

    @PostMapping("/ativacao/detalhes")
    public AtivacaoDetalhesDTO detalhesDaAtivacao(@Valid @RequestBody ConfirmacaoTokenRequestDTO request,
                                                  HttpServletRequest http) {
        exigirDentroDoLimite(http);
        return ativacaoContaService.detalhes(request.token());
    }

    @PostMapping("/ativar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ativar(@Valid @RequestBody AtivarContaRequestDTO request, HttpServletRequest http) {
        exigirDentroDoLimite(http);
        ativacaoContaService.ativar(request.token(), request.senha());
    }

    private void exigirDentroDoLimite(HttpServletRequest http) {
        if (!limite.permitir(http.getRemoteAddr())) {
            throw new ServioException("Muitas tentativas. Aguarde um minuto e tente de novo.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
