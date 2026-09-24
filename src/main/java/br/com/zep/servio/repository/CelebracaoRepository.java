package br.com.zep.servio.repository;

import br.com.zep.servio.model.Celebracao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface CelebracaoRepository extends TenantRepository<Celebracao> {

    List<Celebracao> findByComunidadeIdAndDataBetweenAndActiveTrue(Long comunidadeId, LocalDate inicio, LocalDate fim);

    long countByDataBetweenAndActiveTrue(LocalDate inicio, LocalDate fim);

    List<Celebracao> findByParoquiaIdAndDataGreaterThanEqualAndActiveTrue(Long paroquiaId, LocalDate data);

    /**
     * Celebrações visíveis a quem não é PADRE/ADMIN (Parte 4): as que têm vaga de alguma
     * pastoral do usuário. DISTINCT porque uma celebração pode ter vagas de mais de uma
     * pastoral visível.
     */
    @Query(value = """
            select distinct c from Celebracao c join Vaga v on v.celebracao = c and v.active = true
            where c.paroquiaId = :paroquiaId and c.active = true and v.funcao.pastoral.id in :pastoraisIds
            """,
            countQuery = """
            select count(distinct c) from Celebracao c join Vaga v on v.celebracao = c and v.active = true
            where c.paroquiaId = :paroquiaId and c.active = true and v.funcao.pastoral.id in :pastoraisIds
            """)
    Page<Celebracao> findVisiveisPorPastorais(@Param("paroquiaId") Long paroquiaId,
                                               @Param("pastoraisIds") List<Long> pastoraisIds,
                                               Pageable pageable);
}
