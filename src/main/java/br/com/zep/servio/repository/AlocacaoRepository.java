package br.com.zep.servio.repository;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.enumerated.StatusConvite;

import java.util.Collection;
import java.util.List;

public interface AlocacaoRepository extends TenantRepository<Alocacao> {

    List<Alocacao> findByVagaIdAndActiveTrue(Long vagaId);

    /** Só considera quem de fato ocupa a vaga (status em OCUPANTES), ignorando uma alocação específica. */
    List<Alocacao> findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(Long usuarioId, Collection<StatusConvite> status, Long id);

    boolean existsByVagaIdAndUsuarioIdAndActiveTrueAndStatusInAndIdNot(
            Long vagaId, Long usuarioId, Collection<StatusConvite> status, Long id);

    long countByVagaIdAndActiveTrueAndStatusInAndIdNot(Long vagaId, Collection<StatusConvite> status, Long id);
}
