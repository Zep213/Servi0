package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.mapper.UsuarioPastoralMapper;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.dto.UsuarioPastoralRequestDTO;
import br.com.zep.servio.model.dto.UsuarioPastoralResponseDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem tem papel dentro de uma pastoral é decisão do COORDENADOR daquela pastoral
 * (ou do ADMIN global, para poder inicializar a hierarquia de uma pastoral nova).
 */
@Service
@RequiredArgsConstructor
public class UsuarioPastoralService extends CrudService<UsuarioPastoral, UsuarioPastoralRequestDTO, UsuarioPastoralResponseDTO> {

    private static final int MAX_SECRETARIOS_POR_PASTORAL = 2;

    private final UsuarioPastoralRepository repository;
    private final UsuarioPastoralMapper mapper;
    private final UsuarioRepository usuarioRepository;
    private final PastoralRepository pastoralRepository;
    private final PastoraisPermissao pastoraisPermissao;

    @Override
    protected TenantRepository<UsuarioPastoral> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "UsuarioPastoral";
    }

    @Override
    protected UsuarioPastoralResponseDTO paraResposta(UsuarioPastoral entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected UsuarioPastoral paraEntidade(UsuarioPastoralRequestDTO request) {
        UsuarioPastoral entity = mapper.toEntity(request);
        entity.setPapel(request.papel());
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(UsuarioPastoralRequestDTO request, UsuarioPastoral entity) {
        mapper.updateEntity(request, entity);
        entity.setPapel(request.papel());
        resolverRelacoes(request, entity);
    }

    @Override
    protected void validar(UsuarioPastoral entidade) {
        Long pastoralId = entidade.getPastoral().getId();
        exigirGestor(pastoralId);

        Long id = idOuZero(entidade);
        if (repository.existsByUsuarioIdAndPastoralIdAndActiveTrueAndIdNot(
                entidade.getUsuario().getId(), pastoralId, id)) {
            throw new ConflitoException("Usuário já tem um papel nesta pastoral");
        }
        if (entidade.getPapel() == PapelPastoral.SECRETARIO
                && repository.countByPastoralIdAndPapelAndActiveTrueAndIdNot(pastoralId, PapelPastoral.SECRETARIO, id)
                    >= MAX_SECRETARIOS_POR_PASTORAL) {
            throw new RegraNegocioException("Esta pastoral já tem o máximo de " + MAX_SECRETARIOS_POR_PASTORAL + " secretários");
        }
    }

    /** Atribuir/remover papel é decisão de gestão da pastoral: só o coordenador dela ou o ADMIN. */
    @Override
    @Transactional
    public void desativar(Long id) {
        UsuarioPastoral entidade = obterAtivo(id);
        exigirGestor(entidade.getPastoral().getId());
        entidade.setActive(false);
        repository.save(entidade);
    }

    private void exigirGestor(Long pastoralId) {
        boolean admin = usuario().getPerfil() == Perfil.ADMIN;
        if (!admin && !pastoraisPermissao.temPapel(pastoralId, PapelPastoral.COORDENADOR.name())) {
            throw new AccessDeniedException("Só o coordenador desta pastoral (ou o ADMIN) atribui papéis nela");
        }
    }

    private void resolverRelacoes(UsuarioPastoralRequestDTO request, UsuarioPastoral entity) {
        entity.setUsuario(referencia(usuarioRepository, request.usuarioId(), "Usuario"));
        entity.setPastoral(referencia(pastoralRepository, request.pastoralId(), "Pastoral"));
    }
}
