package br.com.zep.servio.repository;

import br.com.zep.servio.model.NotificacaoEnviada;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoEnviadaRepository extends JpaRepository<NotificacaoEnviada, Long> {

    boolean existsByAlocacaoIdAndTipo(Long alocacaoId, TipoNotificacao tipo);
}
