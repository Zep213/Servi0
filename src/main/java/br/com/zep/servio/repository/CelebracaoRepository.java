package br.com.zep.servio.repository;

import br.com.zep.servio.model.Celebracao;
import java.time.LocalDate;
import java.util.List;
public interface CelebracaoRepository extends TenantRepository<Celebracao> {

    List<Celebracao> findByComunidadeIdAndDataBetweenAndActiveTrue(Long comunidadeId, LocalDate inicio, LocalDate fim);

    long countByDataBetweenAndActiveTrue(LocalDate inicio, LocalDate fim);

    List<Celebracao> findByParoquiaIdAndDataGreaterThanEqualAndActiveTrue(Long paroquiaId, LocalDate data);
}
