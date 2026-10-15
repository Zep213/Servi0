package br.com.zep.servio.repository;

import br.com.zep.servio.model.TokenConta;
import br.com.zep.servio.model.enumerated.TipoTokenConta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TokenContaRepository extends JpaRepository<TokenConta, Long> {

    /**
     * Marca o token como usado só se ele ainda estiver pendente e dentro do prazo. Duas chamadas
     * simultâneas não passam de 1 linha: quem recebe 0 não consumiu (como {@code expirarSePendente}).
     */
    @Modifying
    @Query("""
            update TokenConta t set t.usadoEm = :agora
            where t.tokenHash = :hash and t.tipo = :tipo and t.usadoEm is null and t.expiraEm > :agora
            """)
    int consumir(@Param("hash") String hash, @Param("tipo") TipoTokenConta tipo, @Param("agora") LocalDateTime agora);

    /** Com o usuário carregado, para separar "vencido" de "não existe" sem sessão aberta. */
    @Query("select t from TokenConta t join fetch t.usuario where t.tokenHash = :hash")
    Optional<TokenConta> buscarPorHash(@Param("hash") String hash);

    /** O índice parcial garante no máximo um pendente por tipo e pessoa. */
    Optional<TokenConta> findFirstByUsuarioIdAndTipoAndUsadoEmIsNull(Long usuarioId, TipoTokenConta tipo);

    @Modifying
    @Query("delete from TokenConta t where t.usuario.id = :usuarioId and t.tipo = :tipo and t.usadoEm is null")
    void apagarPendentes(@Param("usuarioId") Long usuarioId, @Param("tipo") TipoTokenConta tipo);

    @Modifying
    @Query("delete from TokenConta t where t.usuario.id = :usuarioId and t.usadoEm is null")
    void apagarPendentes(@Param("usuarioId") Long usuarioId);

    /** Limpeza: tokens já usados ou vencidos há mais tempo que o limite. */
    @Modifying
    @Query("""
            delete from TokenConta t
            where t.usadoEm < :limite or (t.usadoEm is null and t.expiraEm < :limite)
            """)
    int apagarAntesDe(@Param("limite") LocalDateTime limite);
}
