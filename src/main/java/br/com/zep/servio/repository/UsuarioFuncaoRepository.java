package br.com.zep.servio.repository;

import br.com.zep.servio.model.UsuarioFuncao;
import java.util.List;
public interface UsuarioFuncaoRepository extends TenantRepository<UsuarioFuncao> {

    List<UsuarioFuncao> findByUsuarioIdAndActiveTrue(Long usuarioId);

    List<UsuarioFuncao> findByFuncaoIdAndActiveTrue(Long funcaoId);

    /** A pessoa tem a habilidade (função) ativa? Usado pela regra HABILIDADE da elegibilidade. */
    boolean existsByUsuarioIdAndFuncaoIdAndActiveTrue(Long usuarioId, Long funcaoId);

    /** Unicidade só entre habilidades ativas (índice uq_usuario_funcao_ativo); o id ignorado é o do próprio registro. */
    boolean existsByUsuarioIdAndFuncaoIdAndActiveTrueAndIdNot(Long usuarioId, Long funcaoId, Long id);
}
