package br.com.zep.servio.seguranca;

import br.com.zep.servio.security.SessaoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code encerrarTodasAposCommit}: sem transação derruba na hora; com transação, só no commit. */
class SessaoServiceTest {

    @SuppressWarnings("unchecked")
    private final FindByIndexNameSessionRepository<Session> sessoes = mock(FindByIndexNameSessionRepository.class);
    private final SessaoService service = new SessaoService(sessoes);

    @AfterEach
    void limparTransacaoSimulada() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private void comUmaSessao() {
        when(sessoes.findByPrincipalName("maria@servio.dev")).thenReturn(Map.of("s1", mock(Session.class)));
    }

    @Test
    void semTransacaoDerrubaNaHora() {
        comUmaSessao();

        service.encerrarTodasAposCommit("maria@servio.dev");

        verify(sessoes).deleteById("s1");
    }

    @Test
    void comTransacaoSoDerrubaDepoisDoCommit() {
        comUmaSessao();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.encerrarTodasAposCommit("maria@servio.dev");
        verify(sessoes, never()).deleteById(anyString());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(sessoes).deleteById("s1");
    }

    @Test
    void transacaoDesfeitaNaoDerrubaNada() {
        comUmaSessao();
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.encerrarTodasAposCommit("maria@servio.dev");
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(sessoes, never()).deleteById(anyString());
    }
}
