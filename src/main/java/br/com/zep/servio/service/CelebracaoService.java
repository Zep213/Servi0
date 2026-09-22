package br.com.zep.servio.service;

import br.com.zep.servio.mapper.CelebracaoMapper;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.dto.CelebracaoRequestDTO;
import br.com.zep.servio.model.dto.CelebracaoResponseDTO;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CelebracaoService extends CrudService<Celebracao, CelebracaoRequestDTO, CelebracaoResponseDTO> {

    private final CelebracaoRepository repository;
    private final CelebracaoMapper mapper;
    private final ComunidadeRepository comunidadeRepository;

    @Override
    protected JpaRepository<Celebracao, Long> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Celebracao";
    }

    @Override
    protected CelebracaoResponseDTO paraResposta(Celebracao entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Celebracao paraEntidade(CelebracaoRequestDTO request) {
        Celebracao entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(CelebracaoRequestDTO request, Celebracao entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(CelebracaoRequestDTO request, Celebracao entity) {
        entity.setComunidade(referencia(comunidadeRepository, request.comunidadeId(), "Comunidade"));
    }
}
