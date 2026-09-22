package br.com.zep.servio.repository;

import br.com.zep.servio.model.UsuarioFuncao;
import java.util.List;
public interface UsuarioFuncaoRepository extends TenantRepository<UsuarioFuncao> {

    List<UsuarioFuncao> findByUsuarioIdAndActiveTrue(Long usuarioId);

    List<UsuarioFuncao> findByFuncaoIdAndActiveTrue(Long funcaoId);

    boolean existsByUsuarioIdAndFuncaoId(Long usuarioId, Long funcaoId);
}
