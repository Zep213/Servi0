package br.com.zep.servio.service;

import br.com.zep.servio.mapper.PedidoTrocaMapper;
import br.com.zep.servio.model.PedidoTroca;
import br.com.zep.servio.model.dto.PedidoTrocaRequestDTO;
import br.com.zep.servio.model.dto.PedidoTrocaResponseDTO;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.PedidoTrocaRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PedidoTrocaService extends CrudService<PedidoTroca, PedidoTrocaRequestDTO, PedidoTrocaResponseDTO> {

    private final PedidoTrocaRepository repository;
    private final PedidoTrocaMapper mapper;
    private final AlocacaoRepository alocacaoRepository;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected TenantRepository<PedidoTroca> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "PedidoTroca";
    }

    @Override
    protected PedidoTrocaResponseDTO paraResposta(PedidoTroca entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected PedidoTroca paraEntidade(PedidoTrocaRequestDTO request) {
        PedidoTroca entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(PedidoTrocaRequestDTO request, PedidoTroca entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(PedidoTrocaRequestDTO request, PedidoTroca entity) {
        entity.setAlocacao(referencia(alocacaoRepository, request.alocacaoId(), "Alocacao"));
        entity.setSolicitante(referencia(usuarioRepository, usuarioId(), "Usuario"));
        entity.setDestinatario(referenciaOpcional(usuarioRepository, request.destinatarioId(), "Usuario"));
    }
}
