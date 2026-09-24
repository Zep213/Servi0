package br.com.zep.servio.repository;

import br.com.zep.servio.model.Vaga;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
public interface VagaRepository extends TenantRepository<Vaga> {

    List<Vaga> findByCelebracaoIdAndActiveTrue(Long celebracaoId);

    boolean existsByCelebracaoIdAndFuncaoIdAndActiveTrue(Long celebracaoId, Long funcaoId);

    boolean existsByCelebracaoIdAndFuncaoPastoralIdAndActiveTrue(Long celebracaoId, Long pastoralId);

    boolean existsByCelebracaoIdAndFuncaoPastoralIdInAndActiveTrue(Long celebracaoId, List<Long> pastoraisIds);

    /** Vagas visíveis a quem não é PADRE/ADMIN (Parte 4): só de funções de pastorais do usuário. */
    Page<Vaga> findByParoquiaIdAndFuncaoPastoralIdInAndActiveTrue(Long paroquiaId, List<Long> pastoraisIds, Pageable pageable);
}
