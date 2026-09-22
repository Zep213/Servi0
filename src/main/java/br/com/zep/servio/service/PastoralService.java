package br.com.zep.servio.service;

import br.com.zep.servio.mapper.PastoralMapper;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.dto.PastoralRequestDTO;
import br.com.zep.servio.model.dto.PastoralResponseDTO;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PastoralService extends CrudService<Pastoral, PastoralRequestDTO, PastoralResponseDTO> {

    private final PastoralRepository repository;
    private final PastoralMapper mapper;
    private final ParoquiaRepository paroquiaRepository;

    @Override
    protected JpaRepository<Pastoral, Long> repository() {
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
        Pastoral entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(PastoralRequestDTO request, Pastoral entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(PastoralRequestDTO request, Pastoral entity) {
        entity.setParoquia(referencia(paroquiaRepository, request.paroquiaId(), "Paroquia"));
    }
}
