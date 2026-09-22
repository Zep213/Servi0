package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.mapper.UsuarioFuncaoMapper;
import br.com.zep.servio.model.UsuarioFuncao;
import br.com.zep.servio.model.dto.UsuarioFuncaoRequestDTO;
import br.com.zep.servio.model.dto.UsuarioFuncaoResponseDTO;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.UsuarioFuncaoRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsuarioFuncaoService extends CrudService<UsuarioFuncao, UsuarioFuncaoRequestDTO, UsuarioFuncaoResponseDTO> {

    private final UsuarioFuncaoRepository repository;
    private final UsuarioFuncaoMapper mapper;
    private final UsuarioRepository usuarioRepository;
    private final FuncaoRepository funcaoRepository;

    @Override
    protected JpaRepository<UsuarioFuncao, Long> repository() {
        return repository;
    }

    @Override
    protected String nomeRecurso() {
        return "UsuarioFuncao";
    }

    @Override
    protected UsuarioFuncaoResponseDTO paraResposta(UsuarioFuncao entity) {
        return mapper.toResponse(entity);
    }

    @Override
    protected void validarCriacao(UsuarioFuncaoRequestDTO request) {
        if (repository.existsByUsuarioIdAndFuncaoId(request.usuarioId(), request.funcaoId())) {
            throw new ConflitoException("Usuário já possui esta função");
        }
    }

    @Override
    protected UsuarioFuncao paraEntidade(UsuarioFuncaoRequestDTO request) {
        UsuarioFuncao entity = mapper.toEntity(request);
        resolverRelacoes(request, entity);
        return entity;
    }

    @Override
    protected void atualizarEntidade(UsuarioFuncaoRequestDTO request, UsuarioFuncao entity) {
        mapper.updateEntity(request, entity);
        resolverRelacoes(request, entity);
    }

    private void resolverRelacoes(UsuarioFuncaoRequestDTO request, UsuarioFuncao entity) {
        entity.setUsuario(referencia(usuarioRepository, request.usuarioId(), "Usuario"));
        entity.setFuncao(referencia(funcaoRepository, request.funcaoId(), "Funcao"));
    }
}
