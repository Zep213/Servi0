package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.mapper.AlocacaoMapper;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.dto.AlocacaoRequestDTO;
import br.com.zep.servio.model.dto.AlocacaoResponseDTO;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AlocacaoService extends CrudService<Alocacao, AlocacaoRequestDTO, AlocacaoResponseDTO> {

    private final AlocacaoRepository repository;
    private final AlocacaoMapper mapper;
    private final VagaRepository vagaRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected TenantRepository<Alocacao> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Alocacao";
    }

    @Override
    protected AlocacaoResponseDTO paraResposta(Alocacao entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected void validar(Alocacao entidade) {
        if (repository.existsByVagaIdAndUsuarioId(entidade.getVaga().getId(), entidade.getUsuario().getId())) {
            throw new ConflitoException("Usuário já alocado nesta vaga");
        }
    }

    @Override
    protected Alocacao paraEntidade(AlocacaoRequestDTO request) {
        Alocacao entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(AlocacaoRequestDTO request, Alocacao entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(AlocacaoRequestDTO request, Alocacao entity) {
        entity.setVaga(referencia(vagaRepository, request.vagaId(), "Vaga"));
        entity.setUsuario(referencia(usuarioRepository, request.usuarioId(), "Usuario"));
    }
}
