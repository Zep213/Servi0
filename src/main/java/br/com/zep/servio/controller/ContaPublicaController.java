package br.com.zep.servio.controller;

import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.dto.AtivacaoDetalhesDTO;
import br.com.zep.servio.model.dto.AtivarContaRequestDTO;
import br.com.zep.servio.model.dto.ConfirmacaoTokenRequestDTO;
import br.com.zep.servio.config.AsyncConfig;
import br.com.zep.servio.model.dto.RecuperacaoDetalhesDTO;
import br.com.zep.servio.model.dto.RecuperacaoRequestDTO;
import br.com.zep.servio.model.dto.RedefinirSenhaRequestDTO;
import br.com.zep.servio.service.conta.AtivacaoContaService;
import br.com.zep.servio.service.conta.LimiteContaPublica;
import br.com.zep.servio.service.conta.ProtecaoContaService;
import br.com.zep.servio.service.conta.RecuperacaoSenhaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Executor;

/** Páginas públicas de conta (/ativar#token). Sem login; o token é a credencial. */
@Slf4j
@RestController
@RequestMapping("/api/contas")
public class ContaPublicaController {

    private final AtivacaoContaService ativacaoContaService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final ProtecaoContaService protecaoContaService;
    private final LimiteContaPublica limite;
    private final Executor executor;

    /** Construtor escrito à mão: o Lombok não copia o @Qualifier (mesmo motivo do {@code FilaEmail}). */
    public ContaPublicaController(AtivacaoContaService ativacaoContaService,
                                  RecuperacaoSenhaService recuperacaoSenhaService,
                                  ProtecaoContaService protecaoContaService, LimiteContaPublica limite,
                                  @Qualifier(AsyncConfig.EXECUTOR_EMAIL) Executor executor) {
        this.ativacaoContaService = ativacaoContaService;
        this.recuperacaoSenhaService = recuperacaoSenhaService;
        this.protecaoContaService = protecaoContaService;
        this.limite = limite;
        this.executor = executor;
    }

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

    /**
     * Sempre 202 e corpo vazio, exista a conta ou não. O trabalho (achar a conta, emitir o token, enfileirar) roda
     * fora da requisição, para o tempo de resposta também não dizer se a conta existe.
     */
    @PostMapping("/recuperacao")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pedirRecuperacao(@Valid @RequestBody RecuperacaoRequestDTO request, HttpServletRequest http) {
        exigirDentroDoLimite(http);
        String login = request.login();
        try {
            executor.execute(() -> {
                try {
                    recuperacaoSenhaService.solicitar(login);
                } catch (RuntimeException e) {
                    // sem o texto digitado no log: pode ser e-mail ou telefone de alguém
                    log.warn("Falha ao processar pedido de recuperação de senha: {}", e.getClass().getSimpleName());
                }
            });
        } catch (TaskRejectedException e) {
            log.warn("Fila de e-mail cheia: pedido de recuperação de senha descartado");
        }
    }

    @PostMapping("/recuperacao/detalhes")
    public RecuperacaoDetalhesDTO detalhesDaRecuperacao(@Valid @RequestBody ConfirmacaoTokenRequestDTO request,
                                                        HttpServletRequest http) {
        exigirDentroDoLimite(http);
        return recuperacaoSenhaService.detalhes(request.token());
    }

    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void redefinirSenha(@Valid @RequestBody RedefinirSenhaRequestDTO request, HttpServletRequest http) {
        exigirDentroDoLimite(http);
        recuperacaoSenhaService.redefinir(request.token(), request.senha(), http.getHeader("User-Agent"));
    }

    @PostMapping("/protecao/detalhes")
    public RecuperacaoDetalhesDTO detalhesDaProtecao(@Valid @RequestBody ConfirmacaoTokenRequestDTO request,
                                                     HttpServletRequest http) {
        exigirDentroDoLimite(http);
        return protecaoContaService.detalhes(request.token());
    }

    /** "Não fui eu": zera a senha, derruba as sessões e manda o link de recuperação para o e-mail da conta. */
    @PostMapping("/protecao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void proteger(@Valid @RequestBody ConfirmacaoTokenRequestDTO request, HttpServletRequest http) {
        exigirDentroDoLimite(http);
        protecaoContaService.proteger(request.token());
    }

    private void exigirDentroDoLimite(HttpServletRequest http) {
        if (!limite.permitir(http.getRemoteAddr())) {
            throw new ServioException("Muitas tentativas. Aguarde um minuto e tente de novo.",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
