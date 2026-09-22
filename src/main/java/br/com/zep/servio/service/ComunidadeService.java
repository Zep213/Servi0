package br.com.zep.servio.service;

import br.com.zep.servio.mapper.ComunidadeMapper;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.dto.ComunidadeRequestDTO;
import br.com.zep.servio.model.dto.ComunidadeResponseDTO;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ComunidadeService extends CrudService<Comunidade, ComunidadeRequestDTO, ComunidadeResponseDTO> {

    private final ComunidadeRepository repository;
    private final ComunidadeMapper mapper;

    @Override
    protected TenantRepository<Comunidade> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Comunidade";
    }

    @Override
    protected ComunidadeResponseDTO paraResposta(Comunidade entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Comunidade paraEntidade(ComunidadeRequestDTO request) {
        return mapper.toEntity(request);
    }

    @Override
    protected void atualizarEntidade(ComunidadeRequestDTO request, Comunidade entity) {
        mapper.updateEntity(request, entity);
    }
}
