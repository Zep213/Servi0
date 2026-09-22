package br.com.zep.servio.service;

import br.com.zep.servio.mapper.VagaMapper;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.VagaRequestDTO;
import br.com.zep.servio.model.dto.VagaResponseDTO;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.VagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VagaService extends CrudService<Vaga, VagaRequestDTO, VagaResponseDTO> {

    private final VagaRepository repository;
    private final VagaMapper mapper;
    private final CelebracaoRepository celebracaoRepository;
    private final FuncaoRepository funcaoRepository;

    @Override
    protected JpaRepository<Vaga, Long> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Vaga";
    }

    @Override
    protected VagaResponseDTO paraResposta(Vaga entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Vaga paraEntidade(VagaRequestDTO request) {
        Vaga entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(VagaRequestDTO request, Vaga entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(VagaRequestDTO request, Vaga entity) {
        entity.setCelebracao(referencia(celebracaoRepository, request.celebracaoId(), "Celebracao"));
        entity.setFuncao(referencia(funcaoRepository, request.funcaoId(), "Funcao"));
    }
}
