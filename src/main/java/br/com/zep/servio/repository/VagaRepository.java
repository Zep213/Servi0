package br.com.zep.servio.repository;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Vaga;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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

    /** Vaga com função, pastoral e celebração para o aviso de vaga sem elegíveis (Etapa 6, Parte 4). */
    @Query("""
            select v from Vaga v
            join fetch v.funcao f
            join fetch f.pastoral
            left join fetch v.celebracao
            where v.id = :id and v.active = true
            """)
    Optional<Vaga> findParaEmail(@Param("id") Long id);

    /**
     * Painel (Etapa 6, Parte 6): soma de quantidade e de vagas sem quantidade, por celebração,
     * de uma pastoral no intervalo. Uma consulta agrupada; quem junta com as contagens é o serviço.
     */
    @Query("""
            select v.celebracao.id, coalesce(sum(v.quantidade), 0),
                   sum(case when v.quantidade is null then 1 else 0 end)
            from Vaga v
            where v.paroquiaId = :paroquiaId and v.active = true
              and v.funcao.pastoral.id = :pastoralId
              and v.celebracao.data between :inicio and :fim
            group by v.celebracao.id
            """)
    List<Object[]> totaisPorCelebracao(@Param("paroquiaId") Long paroquiaId, @Param("pastoralId") Long pastoralId,
                                       @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Celebrações do intervalo que têm vaga desta pastoral, em ordem de data e hora (painel). */
    @Query("""
            select distinct v.celebracao from Vaga v
            where v.paroquiaId = :paroquiaId and v.active = true
              and v.funcao.pastoral.id = :pastoralId
              and v.celebracao.data between :inicio and :fim
            order by v.celebracao.data, v.celebracao.hora
            """)
    List<Celebracao> celebracoesDaPastoral(@Param("paroquiaId") Long paroquiaId, @Param("pastoralId") Long pastoralId,
                                           @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Vagas de uma pastoral numa celebração, com a função carregada (tela de escala). */
    @Query("""
            select v from Vaga v
            join fetch v.funcao f
            where v.paroquiaId = :paroquiaId and v.active = true
              and v.celebracao.id = :celebracaoId and f.pastoral.id = :pastoralId
            order by f.nome, v.id
            """)
    List<Vaga> vagasDaPastoralNaCelebracao(@Param("paroquiaId") Long paroquiaId, @Param("pastoralId") Long pastoralId,
                                           @Param("celebracaoId") Long celebracaoId);
}
