package br.com.zep.servio.repository;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioPastoralRepository extends TenantRepository<UsuarioPastoral> {

    List<UsuarioPastoral> findByPastoralIdAndActiveTrue(Long pastoralId);

    /** Membros ativos da pastoral com o usuário já carregado, numa consulta só (Etapa 6, Parte 2). */
    @Query("""
            select up from UsuarioPastoral up join fetch up.usuario u
            where up.pastoral.id = :pastoralId and up.paroquiaId = :paroquiaId
              and up.active = true and u.active = true
            order by u.id
            """)
    List<UsuarioPastoral> findMembrosAtivosComUsuario(@Param("pastoralId") Long pastoralId,
                                                       @Param("paroquiaId") Long paroquiaId);

    List<UsuarioPastoral> findByPastoralIdAndPapelAndActiveTrue(Long pastoralId, PapelPastoral papel);

    Optional<UsuarioPastoral> findByUsuarioIdAndPastoralIdAndActiveTrue(Long usuarioId, Long pastoralId);

    List<UsuarioPastoral> findByUsuarioIdAndActiveTrue(Long usuarioId);

    boolean existsByUsuarioIdAndPastoralIdAndActiveTrueAndIdNot(Long usuarioId, Long pastoralId, Long id);

    boolean existsByUsuarioIdAndPapelAndActiveTrue(Long usuarioId, PapelPastoral papel);

    long countByPastoralIdAndPapelAndActiveTrueAndIdNot(Long pastoralId, PapelPastoral papel, Long id);

    /** Pessoas ativas com um papel na pastoral (ex.: coordenadores, para os avisos por e-mail). */
    @Query("""
            select up.usuario from UsuarioPastoral up
            where up.pastoral.id = :pastoralId and up.paroquiaId = :paroquiaId
              and up.papel = :papel and up.active = true and up.usuario.active = true
            order by up.usuario.id
            """)
    List<Usuario> findUsuariosPorPapel(@Param("pastoralId") Long pastoralId,
                                       @Param("paroquiaId") Long paroquiaId,
                                       @Param("papel") PapelPastoral papel);

    /** Participações ativas do usuário com a pastoral já carregada, para /api/me (sem sessão aberta). */
    @Query("""
            select up from UsuarioPastoral up join fetch up.pastoral p
            where up.usuario.id = :usuarioId and up.active = true and p.active = true
            order by p.nome
            """)
    List<UsuarioPastoral> participacoesComPastoral(@Param("usuarioId") Long usuarioId);

    /** Membros ativos de uma pastoral com a pessoa carregada (tela de membros). */
    @Query("""
            select up from UsuarioPastoral up join fetch up.usuario u
            where up.paroquiaId = :paroquiaId and up.pastoral.id = :pastoralId
              and up.active = true and u.active = true
            order by u.nome
            """)
    List<UsuarioPastoral> membrosDaPastoral(@Param("paroquiaId") Long paroquiaId, @Param("pastoralId") Long pastoralId);
}
