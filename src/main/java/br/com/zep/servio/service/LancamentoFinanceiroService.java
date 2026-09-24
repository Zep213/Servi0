package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.mapper.LancamentoFinanceiroMapper;
import br.com.zep.servio.model.LancamentoFinanceiro;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.dto.LancamentoFinanceiroRequestDTO;
import br.com.zep.servio.model.dto.LancamentoFinanceiroResponseDTO;
import br.com.zep.servio.model.dto.ResumoFinanceiroDTO;
import br.com.zep.servio.model.dto.SaldoPastoralDTO;
import br.com.zep.servio.model.dto.SaldoPorPastoralDTO;
import br.com.zep.servio.model.enumerated.TipoLancamento;
import br.com.zep.servio.repository.LancamentoFinanceiroRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Caixa de cada pastoral. Leitura: tesoureiro/coordenador da pastoral, padre ou ADMIN.
 * Escrita: só tesoureiro da pastoral ou ADMIN — padre só lê (PastoraisPermissao 2.3).
 */
@Service
@RequiredArgsConstructor
public class LancamentoFinanceiroService {

    private final LancamentoFinanceiroRepository repository;
    private final LancamentoFinanceiroMapper mapper;
    private final PastoralRepository pastoralRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;

    @Transactional(readOnly = true)
    public Page<LancamentoFinanceiroResponseDTO> listar(Long pastoralId, Pageable pageable) {
        exigirLeitura(pastoralId);
        return repository.findByPastoralIdAndActiveTrue(pastoralId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SaldoPastoralDTO saldo(Long pastoralId) {
        exigirLeitura(pastoralId);
        BigDecimal entradas = somar(repository.findByPastoralIdAndTipoAndActiveTrue(pastoralId, TipoLancamento.ENTRADA));
        BigDecimal saidas = somar(repository.findByPastoralIdAndTipoAndActiveTrue(pastoralId, TipoLancamento.SAIDA));
        return new SaldoPastoralDTO(entradas, saidas, entradas.subtract(saidas));
    }

    @Transactional
    public LancamentoFinanceiroResponseDTO criar(Long pastoralId, LancamentoFinanceiroRequestDTO request) {
        exigirEscrita(pastoralId);
        Pastoral pastoral = pastoral(pastoralId);

        LancamentoFinanceiro entidade = mapper.toEntity(request);
        entidade.setPastoral(pastoral);
        entidade.setParoquiaId(usuarioLogado.paroquiaId());
        return mapper.toResponse(repository.save(entidade));
    }

    @Transactional
    public void desativar(Long pastoralId, Long id) {
        exigirEscrita(pastoralId);
        LancamentoFinanceiro entidade = obter(pastoralId, id);
        entidade.setActive(false);
        repository.save(entidade);
    }

    /** Dashboard do padre (Parte 4): consolidado da paróquia e o mesmo por pastoral. Só PADRE/ADMIN, gate no SecurityConfig. */
    @Transactional(readOnly = true)
    public ResumoFinanceiroDTO resumoConsolidado(LocalDate de, LocalDate ate) {
        List<LancamentoFinanceiro> lancamentos = repository.findByParoquiaIdAndDataLancamentoBetweenAndActiveTrue(
                usuarioLogado.paroquiaId(), de, ate);

        Map<Pastoral, List<LancamentoFinanceiro>> porPastoral = lancamentos.stream()
                .collect(Collectors.groupingBy(LancamentoFinanceiro::getPastoral));
        List<SaldoPorPastoralDTO> resumoPorPastoral = porPastoral.entrySet().stream()
                .map(entry -> {
                    BigDecimal entradas = somarPorTipo(entry.getValue(), TipoLancamento.ENTRADA);
                    BigDecimal saidas = somarPorTipo(entry.getValue(), TipoLancamento.SAIDA);
                    return new SaldoPorPastoralDTO(entry.getKey().getId(), entry.getKey().getNome(),
                            entradas, saidas, entradas.subtract(saidas));
                })
                .toList();

        BigDecimal totalEntradas = somarPorTipo(lancamentos, TipoLancamento.ENTRADA);
        BigDecimal totalSaidas = somarPorTipo(lancamentos, TipoLancamento.SAIDA);
        return new ResumoFinanceiroDTO(totalEntradas, totalSaidas, totalEntradas.subtract(totalSaidas), resumoPorPastoral);
    }

    private BigDecimal somarPorTipo(List<LancamentoFinanceiro> lancamentos, TipoLancamento tipo) {
        return somar(lancamentos.stream().filter(l -> l.getTipo() == tipo).toList());
    }

    private BigDecimal somar(List<LancamentoFinanceiro> lancamentos) {
        return lancamentos.stream().map(LancamentoFinanceiro::getValor).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LancamentoFinanceiro obter(Long pastoralId, Long id) {
        LancamentoFinanceiro entidade = repository.findByIdAndParoquiaIdAndActiveTrue(id, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("LancamentoFinanceiro", id));
        if (!entidade.getPastoral().getId().equals(pastoralId)) {
            throw new RecursoNaoEncontradoException("LancamentoFinanceiro", id);
        }
        return entidade;
    }

    private Pastoral pastoral(Long pastoralId) {
        return pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pastoral", pastoralId));
    }

    private void exigirLeitura(Long pastoralId) {
        if (!pastoraisPermissao.podeLerFinanceiro(pastoralId)) {
            throw new AccessDeniedException("Você não tem acesso ao financeiro desta pastoral");
        }
    }

    private void exigirEscrita(Long pastoralId) {
        if (!pastoraisPermissao.podeEscreverFinanceiro(pastoralId)) {
            throw new AccessDeniedException("Só o tesoureiro desta pastoral lança no financeiro");
        }
    }
}
