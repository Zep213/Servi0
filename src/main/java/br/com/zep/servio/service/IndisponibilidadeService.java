package br.com.zep.servio.service;

import br.com.zep.servio.mapper.IndisponibilidadeMapper;
import br.com.zep.servio.model.Indisponibilidade;
import br.com.zep.servio.model.dto.IndisponibilidadeRequestDTO;
import br.com.zep.servio.model.dto.IndisponibilidadeResponseDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.IndisponibilidadeRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IndisponibilidadeService extends CrudService<Indisponibilidade, IndisponibilidadeRequestDTO, IndisponibilidadeResponseDTO> {

    private final IndisponibilidadeRepository repository;
    private final IndisponibilidadeMapper mapper;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected TenantRepository<Indisponibilidade> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Indisponibilidade";
    }

    @Override
    protected IndisponibilidadeResponseDTO paraResposta(Indisponibilidade entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Indisponibilidade paraEntidade(IndisponibilidadeRequestDTO request) {
        Indisponibilidade entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(IndisponibilidadeRequestDTO request, Indisponibilidade entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(IndisponibilidadeRequestDTO request, Indisponibilidade entity) {
        Long alvo = request.usuarioId();
        if (alvo != null && !alvo.equals(usuarioId())
                && usuario().getPerfil() != Perfil.ADMIN && usuario().getPerfil() != Perfil.COORDENADOR) {
            throw new AccessDeniedException("Você só pode marcar a própria indisponibilidade");
        }
        entity.setUsuario(referencia(usuarioRepository, alvo != null ? alvo : usuarioId(), "Usuario"));
    }
}
