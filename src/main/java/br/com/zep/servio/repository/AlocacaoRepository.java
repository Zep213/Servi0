package br.com.zep.servio.repository;

import br.com.zep.servio.model.Alocacao;
import java.util.List;
public interface AlocacaoRepository extends TenantRepository<Alocacao> {

    List<Alocacao> findByUsuarioIdAndActiveTrue(Long usuarioId);

    List<Alocacao> findByVagaIdAndActiveTrue(Long vagaId);

    boolean existsByVagaIdAndUsuarioId(Long vagaId, Long usuarioId);
}
