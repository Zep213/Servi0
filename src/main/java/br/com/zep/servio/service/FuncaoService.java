package br.com.zep.servio.service;

import br.com.zep.servio.mapper.FuncaoMapper;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.dto.FuncaoRequestDTO;
import br.com.zep.servio.model.dto.FuncaoResponseDTO;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FuncaoService extends CrudService<Funcao, FuncaoRequestDTO, FuncaoResponseDTO> {

    private final FuncaoRepository repository;
    private final FuncaoMapper mapper;
    private final PastoralRepository pastoralRepository;

    /** Funções de uma pastoral (GET /api/funcoes?pastoralId=). Pastoral de outra paróquia vem vazia. */
    @Transactional(readOnly = true)
    public Page<FuncaoResponseDTO> listarDaPastoral(Long pastoralId, Pageable pageable) {
        return repository.findByParoquiaIdAndPastoralIdAndActiveTrue(paroquiaId(), pastoralId, pageable)
                .map(this::paraResposta);
    }

    @Override
    protected TenantRepository<Funcao> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Funcao";
    }

    @Override
    protected FuncaoResponseDTO paraResposta(Funcao entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Funcao paraEntidade(FuncaoRequestDTO request) {
        Funcao entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(FuncaoRequestDTO request, Funcao entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(FuncaoRequestDTO request, Funcao entity) {
        entity.setPastoral(referencia(pastoralRepository, request.pastoralId(), "Pastoral"));
    }
}
