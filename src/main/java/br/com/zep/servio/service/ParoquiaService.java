package br.com.zep.servio.service;

import br.com.zep.servio.mapper.ParoquiaMapper;
import br.com.zep.servio.model.Paroquia;
import br.com.zep.servio.model.dto.ParoquiaRequestDTO;
import br.com.zep.servio.model.dto.ParoquiaResponseDTO;
import br.com.zep.servio.repository.ParoquiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParoquiaService extends CrudService<Paroquia, ParoquiaRequestDTO, ParoquiaResponseDTO> {

    private final ParoquiaRepository repository;
    private final ParoquiaMapper mapper;

    @Override
    protected JpaRepository<Paroquia, Long> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Paroquia";
    }

    @Override
    protected ParoquiaResponseDTO paraResposta(Paroquia entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Paroquia paraEntidade(ParoquiaRequestDTO request) {
        Paroquia entity = mapper.toEntity(request);
        return entity;
    }

    @Override
    protected void atualizarEntidade(ParoquiaRequestDTO request, Paroquia entity) {
        mapper.updateEntity(request, entity);
    }
}
