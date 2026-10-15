package br.com.zep.servio.security;

import lombok.RequiredArgsConstructor;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Derruba na hora todas as sessões de um usuário (perfil alterado, senha trocada, desativado). */
@Service
@RequiredArgsConstructor
public class SessaoService {

    private final FindByIndexNameSessionRepository<? extends Session> sessoes;

    public void encerrarTodas(String email) {
        sessoes.findByPrincipalName(email).keySet().forEach(sessoes::deleteById);
    }

    /**
     * Com transação ativa, só derruba as sessões quando ela comitar: se a alteração for desfeita (erro, 409,
     * 403), a pessoa não é deslogada à toa. Sem transação, derruba na hora. {@link #encerrarTodas} continua
     * valendo dentro da transação, para os usos que já existem.
     */
    public void encerrarTodasAposCommit(String email) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            encerrarTodas(email);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                encerrarTodas(email);
            }
        });
    }
}
