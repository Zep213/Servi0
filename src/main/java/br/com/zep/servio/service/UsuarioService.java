package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.mapper.UsuarioMapper;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.UsuarioRequestDTO;
import br.com.zep.servio.model.dto.UsuarioResponseDTO;
import br.com.zep.servio.model.dto.UsuarioUpdateDTO;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService extends CrudService<Usuario, UsuarioRequestDTO, UsuarioResponseDTO> {

    private final UsuarioRepository repository;
    private final UsuarioMapper mapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected TenantRepository<Usuario> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "Usuario";
    }

    @Override
    protected UsuarioResponseDTO paraResposta(Usuario entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected Usuario paraEntidade(UsuarioRequestDTO request) {
        Usuario entity = mapper.toEntity(request);
        entity.setSenha(passwordEncoder.encode(request.senha()));
        return entity;
    }

    /** Não usado: o update tem DTO próprio (ver atualizar). */
    @Override
    protected void atualizarEntidade(UsuarioRequestDTO request, Usuario entity) {
        throw new UnsupportedOperationException("Use atualizar(id, UsuarioUpdateDTO)");
    }

    @Override
    protected void validar(Usuario entidade) {
        if (repository.existsByEmailIgnoreCaseAndActiveTrueAndIdNot(entidade.getEmail(), idOuZero(entidade))) {
            throw new ConflitoException("Já existe um usuário ativo com este e-mail");
        }
    }

    @Transactional
    public UsuarioResponseDTO atualizar(Long id, UsuarioUpdateDTO request) {
        Usuario usuario = obterAtivo(id);
        mapper.updateEntity(request, usuario);
        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.setSenha(passwordEncoder.encode(request.senha()));
        }
        validar(usuario);
        return paraResposta(repository.save(usuario));
        // etapa 3: encerrar as sessões quando perfil, e-mail ou senha mudarem
    }
}
