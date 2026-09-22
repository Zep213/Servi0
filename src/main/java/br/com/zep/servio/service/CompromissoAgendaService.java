package br.com.zep.servio.service;

import br.com.zep.servio.mapper.CompromissoAgendaMapper;
import br.com.zep.servio.model.CompromissoAgenda;
import br.com.zep.servio.model.dto.CompromissoAgendaRequestDTO;
import br.com.zep.servio.model.dto.CompromissoAgendaResponseDTO;
import br.com.zep.servio.repository.CompromissoAgendaRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompromissoAgendaService extends CrudService<CompromissoAgenda, CompromissoAgendaRequestDTO, CompromissoAgendaResponseDTO> {

    private final CompromissoAgendaRepository repository;
    private final CompromissoAgendaMapper mapper;
    private final UsuarioRepository usuarioRepository;
    private final ComunidadeRepository comunidadeRepository;

    @Override
    protected TenantRepository<CompromissoAgenda> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "CompromissoAgenda";
    }

    @Override
    protected CompromissoAgendaResponseDTO paraResposta(CompromissoAgenda entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected CompromissoAgenda paraEntidade(CompromissoAgendaRequestDTO request) {
        CompromissoAgenda entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(CompromissoAgendaRequestDTO request, CompromissoAgenda entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(CompromissoAgendaRequestDTO request, CompromissoAgenda entity) {
        entity.setPadre(referencia(usuarioRepository, usuarioId(), "Usuario"));
        entity.setComunidade(referenciaOpcional(comunidadeRepository, request.comunidadeId(), "Comunidade"));
    }
}
