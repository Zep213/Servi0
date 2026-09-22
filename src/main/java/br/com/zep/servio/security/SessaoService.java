package br.com.zep.servio.security;

import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** Derruba na hora todas as sessões de um usuário (perfil alterado, senha trocada, desativado). */
@Service
@RequiredArgsConstructor
public class SessaoService {

    private final FindByIndexNameSessionRepository<? extends Session> sessoes;

    public void encerrarTodas(String email) {
        sessoes.findByPrincipalName(email).keySet().forEach(sessoes::deleteById);
    }
}
