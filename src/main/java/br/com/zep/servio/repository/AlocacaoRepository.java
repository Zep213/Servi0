package br.com.zep.servio.repository;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.enumerated.StatusConvite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AlocacaoRepository extends TenantRepository<Alocacao> {

    /** Último serviço (celebração já passada, com convite aceito) de cada pessoa da lista. */
    interface UltimoServico {
        Long getUsuarioId();

        LocalDate getUltimo();
    }

    List<Alocacao> findByVagaIdAndActiveTrue(Long vagaId);

    /** Só quem tem o token (guardado como SHA-256) acha a alocação; alocação inativa não conta. */
    Optional<Alocacao> findByTokenHashAndActiveTrue(String tokenHash);

    /** Alocações visíveis a quem não é PADRE/ADMIN (Parte 4): só de funções de pastorais do usuário. */
    Page<Alocacao> findByParoquiaIdAndVagaFuncaoPastoralIdInAndActiveTrue(
            Long paroquiaId, List<Long> pastoraisIds, Pageable pageable);

    /** Só considera quem de fato ocupa a vaga (status em OCUPANTES), ignorando uma alocação específica. */
    List<Alocacao> findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(Long usuarioId, Collection<StatusConvite> status, Long id);

    boolean existsByVagaIdAndUsuarioIdAndActiveTrueAndStatusInAndIdNot(
            Long vagaId, Long usuarioId, Collection<StatusConvite> status, Long id);

    long countByVagaIdAndActiveTrueAndStatusInAndIdNot(Long vagaId, Collection<StatusConvite> status, Long id);

    /** Uma consulta para a pastoral inteira: quem serviu por último (ACEITA, celebração até hoje). */
    @Query("""
            select a.usuario.id as usuarioId, max(a.vaga.celebracao.data) as ultimo
            from Alocacao a
            where a.paroquiaId = :paroquiaId and a.active = true and a.status = :aceita
              and a.usuario.id in :usuarioIds and a.vaga.celebracao.data <= :hoje
            group by a.usuario.id
            """)
    List<UltimoServico> ultimosServicos(@Param("paroquiaId") Long paroquiaId,
                                        @Param("aceita") StatusConvite aceita,
                                        @Param("usuarioIds") Collection<Long> usuarioIds,
                                        @Param("hoje") LocalDate hoje);
}
