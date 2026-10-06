package br.com.zep.servio.repository;

import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AlteracaoPendenteRepository extends TenantRepository<AlteracaoPendente> {

    List<AlteracaoPendente> findByPastoralIdAndStatusAndActiveTrue(Long pastoralId, StatusAlteracaoPendente status);

    List<AlteracaoPendente> findByAlocacaoId(Long alocacaoId);

    /** Parte 4: só das pastorais que o usuário gerencia (COORDENADOR/VICE); todas para PADRE/ADMIN. */
    Page<AlteracaoPendente> findByParoquiaIdAndPastoralIdInAndActiveTrue(
            Long paroquiaId, List<Long> pastoraisIds, Pageable pageable);

    /** Painel: alterações do vice aguardando confirmação, com as pessoas e a função já carregadas. */
    @Query("""
            select p from AlteracaoPendente p
            left join fetch p.usuarioAnterior
            left join fetch p.usuarioNovo
            left join fetch p.vagaNova vn
            left join fetch vn.funcao
            where p.paroquiaId = :paroquiaId and p.pastoral.id = :pastoralId
              and p.status = :status and p.active = true
            order by p.id
            """)
    List<AlteracaoPendente> aguardandoComDetalhes(@Param("paroquiaId") Long paroquiaId, @Param("pastoralId") Long pastoralId,
                                                  @Param("status") StatusAlteracaoPendente status);
}
