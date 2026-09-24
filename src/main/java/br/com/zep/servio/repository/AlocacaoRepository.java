package br.com.zep.servio.repository;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.enumerated.StatusConvite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface AlocacaoRepository extends TenantRepository<Alocacao> {

    List<Alocacao> findByVagaIdAndActiveTrue(Long vagaId);

    /** Alocações visíveis a quem não é PADRE/ADMIN (Parte 4): só de funções de pastorais do usuário. */
    Page<Alocacao> findByParoquiaIdAndVagaFuncaoPastoralIdInAndActiveTrue(
            Long paroquiaId, List<Long> pastoraisIds, Pageable pageable);

    /** Só considera quem de fato ocupa a vaga (status em OCUPANTES), ignorando uma alocação específica. */
    List<Alocacao> findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(Long usuarioId, Collection<StatusConvite> status, Long id);

    boolean existsByVagaIdAndUsuarioIdAndActiveTrueAndStatusInAndIdNot(
            Long vagaId, Long usuarioId, Collection<StatusConvite> status, Long id);

    long countByVagaIdAndActiveTrueAndStatusInAndIdNot(Long vagaId, Collection<StatusConvite> status, Long id);
}
