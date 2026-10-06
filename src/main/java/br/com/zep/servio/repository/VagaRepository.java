package br.com.zep.servio.repository;

import br.com.zep.servio.model.Vaga;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VagaRepository extends TenantRepository<Vaga> {

    List<Vaga> findByCelebracaoIdAndActiveTrue(Long celebracaoId);

    boolean existsByCelebracaoIdAndFuncaoIdAndActiveTrue(Long celebracaoId, Long funcaoId);

    boolean existsByCelebracaoIdAndFuncaoPastoralIdAndActiveTrue(Long celebracaoId, Long pastoralId);

    boolean existsByCelebracaoIdAndFuncaoPastoralIdInAndActiveTrue(Long celebracaoId, List<Long> pastoraisIds);

    /** Vagas visíveis a quem não é PADRE/ADMIN (Parte 4): só de funções de pastorais do usuário. */
    Page<Vaga> findByParoquiaIdAndFuncaoPastoralIdInAndActiveTrue(Long paroquiaId, List<Long> pastoraisIds, Pageable pageable);

    /**
     * Trava a linha da vaga até o fim da transação (Etapa 6): sorteio, escalação e substituição
     * leem a ocupação DEPOIS deste lock, então duas operações concorrentes na mesma vaga não
     * passam da quantidade. Sem joins, para o FOR UPDATE não travar outras tabelas.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vaga v where v.id = :id and v.paroquiaId = :paroquiaId and v.active = true")
    Optional<Vaga> findByIdParaEscalarComLock(@Param("id") Long id, @Param("paroquiaId") Long paroquiaId);
}
