package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.mapper.UsuarioMapper;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.UsuarioRequestDTO;
import br.com.zep.servio.model.dto.UsuarioResponseDTO;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioService extends CrudService<Usuario, UsuarioRequestDTO, UsuarioResponseDTO> {

    private final UsuarioRepository repository;
    private final UsuarioMapper mapper;
    private final ParoquiaRepository paroquiaRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    protected JpaRepository<Usuario, Long> repository() {
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
    protected void validarCriacao(UsuarioRequestDTO request) {
        if (repository.existsByEmailAndParoquiaId(request.email(), request.paroquiaId())) {
            throw new ConflitoException("Já existe um usuário com este e-mail na paróquia");
        }
    }

    @Override
    protected Usuario paraEntidade(UsuarioRequestDTO request) {
        Usuario entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(UsuarioRequestDTO request, Usuario entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(UsuarioRequestDTO request, Usuario entity) {
        entity.setSenha(passwordEncoder.encode(request.senha()));
        entity.setParoquia(referencia(paroquiaRepository, request.paroquiaId(), "Paroquia"));
    }
}
