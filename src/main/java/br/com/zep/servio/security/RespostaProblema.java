package br.com.zep.servio.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Resposta de erro nos filtros, no mesmo formato ProblemDetail usado pela API. */
final class RespostaProblema {

    private RespostaProblema() {
    }

    /** Só para mensagens fixas do código: nunca passar texto vindo do usuário. */
    static void escrever(HttpServletResponse response, int status, String detalhe) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detalhe + "\"}");
    }
}
