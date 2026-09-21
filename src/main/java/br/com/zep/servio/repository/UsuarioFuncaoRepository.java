package br.com.zep.servio.repository;

import br.com.zep.servio.model.UsuarioFuncao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface UsuarioFuncaoRepository extends JpaRepository<UsuarioFuncao, Long> {

    List<UsuarioFuncao> findByUsuarioIdAndActiveTrue(Long usuarioId);

    List<UsuarioFuncao> findByFuncaoIdAndActiveTrue(Long funcaoId);

    boolean existsByUsuarioIdAndFuncaoId(Long usuarioId, Long funcaoId);
}
