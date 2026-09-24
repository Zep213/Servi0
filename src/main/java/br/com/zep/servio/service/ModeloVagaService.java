package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.mapper.ModeloVagaMapper;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.ModeloVaga;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.dto.ModeloVagaRequestDTO;
import br.com.zep.servio.model.dto.ModeloVagaResponseDTO;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ModeloVagaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * CRUD de modelos de vaga e aplicação às celebrações futuras (Parte 3.3).
 * Autorização (COORDENADOR da pastoral, ou PADRE/ADMIN) é checada no @PreAuthorize do
 * controller, igual ao PastoralConfigController — aqui só regra de negócio.
 */
@Service
@RequiredArgsConstructor
public class ModeloVagaService {

    private final ModeloVagaRepository repository;
    private final ModeloVagaMapper mapper;
    private final PastoralRepository pastoralRepository;
    private final FuncaoRepository funcaoRepository;
    private final CelebracaoRepository celebracaoRepository;
    private final CoberturaAutomaticaService coberturaAutomaticaService;
    private final UsuarioLogado usuarioLogado;

    @Transactional(readOnly = true)
    public List<ModeloVagaResponseDTO> listar(Long pastoralId) {
        pastoral(pastoralId);
        return repository.findByPastoralIdAndActiveTrue(pastoralId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public ModeloVagaResponseDTO criar(Long pastoralId, ModeloVagaRequestDTO request) {
        Pastoral pastoral = pastoral(pastoralId);
        ModeloVaga entidade = mapper.toEntity(request);
        entidade.setPastoral(pastoral);
        entidade.setFuncao(funcaoDaPastoral(pastoralId, request.funcaoId()));
        entidade.setParoquiaId(usuarioLogado.paroquiaId());
        return mapper.toResponse(repository.save(entidade));
    }

    @Transactional
    public ModeloVagaResponseDTO atualizar(Long pastoralId, Long id, ModeloVagaRequestDTO request) {
        ModeloVaga entidade = obter(pastoralId, id);
        mapper.updateEntity(request, entidade);
        entidade.setFuncao(funcaoDaPastoral(pastoralId, request.funcaoId()));
        return mapper.toResponse(repository.save(entidade));
    }

    @Transactional
    public void desativar(Long pastoralId, Long id) {
        ModeloVaga entidade = obter(pastoralId, id);
        entidade.setActive(false);
        repository.save(entidade);
    }

    @Transactional
    public int aplicarFuturas(Long pastoralId) {
        Pastoral pastoral = pastoral(pastoralId);
        List<Celebracao> futuras = celebracaoRepository.findByParoquiaIdAndDataGreaterThanEqualAndActiveTrue(
                usuarioLogado.paroquiaId(), LocalDate.now());
        return coberturaAutomaticaService.aplicarFuturas(pastoral, futuras);
    }

    private Funcao funcaoDaPastoral(Long pastoralId, Long funcaoId) {
        Funcao funcao = funcaoRepository.findByIdAndParoquiaIdAndActiveTrue(funcaoId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Funcao", funcaoId));
        if (!funcao.getPastoral().getId().equals(pastoralId)) {
            throw new RegraNegocioException("Função do modelo precisa ser da mesma pastoral");
        }
        return funcao;
    }

    private ModeloVaga obter(Long pastoralId, Long id) {
        return repository.findByIdAndPastoralIdAndActiveTrue(id, pastoralId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("ModeloVaga", id));
    }

    private Pastoral pastoral(Long pastoralId) {
        return pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pastoral", pastoralId));
    }
}
