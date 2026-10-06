package br.com.zep.servio.repository;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoNotificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    /** Alocação com o que o e-mail precisa, sem depender de sessão aberta (o envio roda depois do commit). */
    @Query("""
            select a from Alocacao a
            join fetch a.usuario
            join fetch a.vaga v
            join fetch v.funcao f
            join fetch f.pastoral
            left join fetch v.celebracao
            where a.id = :id and a.active = true
            """)
    Optional<Alocacao> findParaEmail(@Param("id") Long id);

    /** Ids de convites pendentes com prazo vencido, de todas as paróquias (job, sem usuário logado). */
    @Query("""
            select a.id from Alocacao a
            where a.active = true and a.status = :pendente and a.dataLimiteResposta < :agora
            """)
    List<Long> idsPendentesVencidos(@Param("pendente") StatusConvite pendente, @Param("agora") LocalDateTime agora);

    /**
     * Expira um convite só se ele ainda estiver pendente e vencido. Segundo job, ou resposta
     * concorrente, não passa de zero linhas: quem chama avisa só quando devolve 1.
     */
    @Modifying
    @Query("""
            update Alocacao a set a.status = :expirada, a.tokenHash = null
            where a.id = :id and a.status = :pendente and a.dataLimiteResposta < :agora
            """)
    int expirarSePendente(@Param("id") Long id, @Param("pendente") StatusConvite pendente,
                          @Param("expirada") StatusConvite expirada, @Param("agora") LocalDateTime agora);

    /** Alocações com o que o lembrete precisa, sem o registro daquele tipo ainda gravado. */
    @Query("""
            select a from Alocacao a
            join fetch a.usuario
            join fetch a.vaga v
            join fetch v.funcao f
            join fetch f.pastoral
            join fetch v.celebracao c
            where a.active = true and a.status = :status
              and c.data between :inicio and :fim
              and not exists (select n.id from NotificacaoEnviada n where n.alocacao = a and n.tipo = :tipo)
            """)
    List<Alocacao> candidatosLembrete(@Param("status") StatusConvite status, @Param("tipo") TipoNotificacao tipo,
                                      @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Convites pendentes ainda dentro do prazo, sem lembrete de resposta gravado (o filtro de janela é por pastoral, em Java). */
    @Query("""
            select a from Alocacao a
            join fetch a.usuario
            join fetch a.vaga v
            join fetch v.funcao f
            join fetch f.pastoral
            left join fetch v.celebracao
            where a.active = true and a.status = :status
              and a.dataLimiteResposta > :agora
              and not exists (select n.id from NotificacaoEnviada n where n.alocacao = a and n.tipo = :tipo)
            """)
    List<Alocacao> candidatosLembreteResposta(@Param("status") StatusConvite status, @Param("tipo") TipoNotificacao tipo,
                                              @Param("agora") LocalDateTime agora);
}
