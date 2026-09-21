package br.com.zep.servio.repository;

import br.com.zep.servio.model.Alocacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AlocacaoRepository extends JpaRepository<Alocacao, Long> {

    List<Alocacao> findByUsuarioIdAndActiveTrue(Long usuarioId);

    List<Alocacao> findByVagaIdAndActiveTrue(Long vagaId);

    boolean existsByVagaIdAndUsuarioId(Long vagaId, Long usuarioId);
}
