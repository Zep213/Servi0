package br.com.zep.servio.service;

import br.com.zep.servio.mapper.FuncaoMapper;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.dto.FuncaoRequestDTO;
import br.com.zep.servio.model.dto.FuncaoResponseDTO;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.PastoralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FuncaoService extends CrudService<Funcao, FuncaoRequestDTO, FuncaoResponseDTO> {

    private final FuncaoRepository repository;
    private final FuncaoMapper mapper;
    private final PastoralRepository pastoralRepository;

    @Override
    protected JpaRepository<Funcao, Long> repository() {
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
