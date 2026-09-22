package br.com.zep.servio.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Respostas do login. Sucesso: 204 (o front chama /api/me em seguida).
 * Falha: sempre a mesma mensagem, exista o e-mail ou não, para não revelar quem tem conta.
 */
@Component
@RequiredArgsConstructor
public class LoginHandlers {

    private final TentativasLogin tentativas;

    public void sucesso(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        UsuarioPrincipal usuario = (UsuarioPrincipal) auth.getPrincipal();
        tentativas.limpar(TentativasLogin.chaveIp(request));
        tentativas.limpar(TentativasLogin.chaveEmail(usuario.getEmail()));
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    public void falha(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        tentativas.registrarFalha(TentativasLogin.chaveIp(request));
        String email = request.getParameter("email");
        if (email != null) {
            tentativas.registrarFalha(TentativasLogin.chaveEmail(email));
        }
        RespostaProblema.escrever(response, 401, "E-mail ou senha inválidos");
    }
}
