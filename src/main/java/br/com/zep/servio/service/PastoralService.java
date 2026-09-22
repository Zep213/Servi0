package br.com.zep.servio.service;

import br.com.zep.servio.mapper.PastoralMapper;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.dto.PastoralRequestDTO;
import br.com.zep.servio.model.dto.PastoralResponseDTO;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PastoralService extends CrudService<Pastoral, PastoralRequestDTO, PastoralResponseDTO> {

    private final PastoralRepository repository;
    private final PastoralMapper mapper;

    @Override
    protected TenantRepository<Pastoral> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Pastoral";
    }

    @Override
    protected PastoralResponseDTO paraResposta(Pastoral entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Pastoral paraEntidade(PastoralRequestDTO request) {
        return mapper.toEntity(request);
    }

    @Override
    protected void atualizarEntidade(PastoralRequestDTO request, Pastoral entity) {
        mapper.updateEntity(request, entity);
    }
}
