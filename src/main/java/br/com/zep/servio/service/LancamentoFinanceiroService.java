package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.mapper.LancamentoFinanceiroMapper;
import br.com.zep.servio.model.LancamentoFinanceiro;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.dto.LancamentoFinanceiroRequestDTO;
import br.com.zep.servio.model.dto.LancamentoFinanceiroResponseDTO;
import br.com.zep.servio.model.dto.SaldoPastoralDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
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
import java.util.List;

/**
 * Caixa de cada pastoral: acesso restrito a tesoureiro/coordenador daquela pastoral
 * (não do global Perfil da paróquia), checado via PastoraisPermissao.
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
        exigirAcesso(pastoralId);
        return repository.findByPastoralIdAndActiveTrue(pastoralId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public SaldoPastoralDTO saldo(Long pastoralId) {
        exigirAcesso(pastoralId);
        BigDecimal entradas = somar(repository.findByPastoralIdAndTipoAndActiveTrue(pastoralId, TipoLancamento.ENTRADA));
        BigDecimal saidas = somar(repository.findByPastoralIdAndTipoAndActiveTrue(pastoralId, TipoLancamento.SAIDA));
        return new SaldoPastoralDTO(entradas, saidas, entradas.subtract(saidas));
    }

    @Transactional
    public LancamentoFinanceiroResponseDTO criar(Long pastoralId, LancamentoFinanceiroRequestDTO request) {
        exigirAcesso(pastoralId);
        Pastoral pastoral = pastoral(pastoralId);

        LancamentoFinanceiro entidade = mapper.toEntity(request);
        entidade.setPastoral(pastoral);
        entidade.setParoquiaId(usuarioLogado.paroquiaId());
        return mapper.toResponse(repository.save(entidade));
    }

    @Transactional
    public void desativar(Long pastoralId, Long id) {
        exigirAcesso(pastoralId);
        LancamentoFinanceiro entidade = obter(pastoralId, id);
        entidade.setActive(false);
        repository.save(entidade);
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

    private void exigirAcesso(Long pastoralId) {
        if (!pastoraisPermissao.temQualquerPapel(pastoralId, PapelPastoral.TESOUREIRO.name(), PapelPastoral.COORDENADOR.name())) {
            throw new AccessDeniedException("Só o tesoureiro ou coordenador desta pastoral acessa o financeiro");
        }
    }
}
