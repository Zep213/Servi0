package br.com.zep.servio.repository;

import br.com.zep.servio.model.EmailSaida;
import br.com.zep.servio.model.enumerated.StatusEmail;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EmailSaidaRepository extends JpaRepository<EmailSaida, Long> {

    /** Trava a linha pra uma thread/instância só processar por vez; outra tentativa simultânea volta vazio. */
    @Query(value = "select * from email_saida where id = :id and status = 'PENDENTE' for update skip locked",
            nativeQuery = true)
    Optional<EmailSaida> travarPendente(@Param("id") Long id);

    /** Ids prontos pra (re)tentar, mais antigos primeiro; o job limita o lote pelo Pageable. */
    @Query("""
            select e.id from EmailSaida e
            where e.status = :status and e.proximaTentativaEm <= :agora
            order by e.proximaTentativaEm
            """)
    List<Long> idsProntos(@Param("status") StatusEmail status, @Param("agora") LocalDateTime agora, Pageable pageable);

    @Modifying
    @Query("delete from EmailSaida e where e.status = br.com.zep.servio.model.enumerated.StatusEmail.ENVIADO and e.enviadoEm < :limite")
    void apagarEnviadosAntesDe(@Param("limite") LocalDateTime limite);
}
