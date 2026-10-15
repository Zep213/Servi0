package br.com.zep.servio.security;

import br.com.zep.servio.repository.UsuarioRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Respostas do login. Sucesso: 204 (o front chama /api/me em seguida).
 * Falha: sempre a mesma mensagem, exista a conta ou não, para não revelar quem tem conta.
 */
@Component
@RequiredArgsConstructor
public class LoginHandlers {

    private final TentativasLogin tentativas;
    private final ChaveLogin chaveLogin;
    private final UsuarioRepository usuarioRepository;
    private final SessaoDuracao sessaoDuracao;

    public void sucesso(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        UsuarioPrincipal usuario = (UsuarioPrincipal) auth.getPrincipal();
        tentativas.limpar(TentativasLogin.chaveIp(request));
        usuarioRepository.findById(usuario.getId())
                .ifPresent(u -> chaveLogin.chavesDaConta(u).forEach(tentativas::limpar));
        // o id da sessão já foi trocado (session fixation) antes deste handler
        request.getSession().setMaxInactiveInterval((int) sessaoDuracao.para(usuario.getId(), usuario.getPerfil()).toSeconds());
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    public void falha(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        tentativas.registrarFalha(TentativasLogin.chaveIp(request));
        chaveLogin.chaves(request.getParameter("login")).forEach(tentativas::registrarFalha);
        RespostaProblema.escrever(response, 401, "E-mail, telefone ou senha inválidos");
    }
}
