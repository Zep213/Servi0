package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.mapper.UsuarioMapper;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.UsuarioRequestDTO;
import br.com.zep.servio.model.dto.UsuarioResponseDTO;
import br.com.zep.servio.model.dto.UsuarioResumoDTO;
import br.com.zep.servio.model.dto.UsuarioUpdateDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.TenantRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.SessaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService extends CrudService<Usuario, UsuarioRequestDTO, UsuarioResponseDTO> {

    private final UsuarioRepository repository;
    private final UsuarioMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final SessaoService sessaoService;
    private final PastoraisPermissao pastoraisPermissao;

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
        exigirPodeDefinirPerfil(entidade.getPerfil());
        if (repository.existsByEmailIgnoreCaseAndActiveTrueAndIdNot(entidade.getEmail(), idOuZero(entidade))) {
            throw new ConflitoException("Já existe um usuário ativo com este e-mail");
        }
    }

    /**
     * ADMIN cria qualquer perfil; PADRE cria PADRE e SERVIDOR; coordenador de alguma
     * pastoral cria só SERVIDOR (2.4). Como editar/desativar já é restrito a PADRE/ADMIN
     * no SecurityConfig, o ramo do coordenador só é alcançado ao criar.
     */
    private void exigirPodeDefinirPerfil(Perfil perfilAlvo) {
        if (pastoraisPermissao.ehAdmin()) {
            return;
        }
        if (pastoraisPermissao.ehPadre()) {
            if (perfilAlvo == Perfil.PADRE || perfilAlvo == Perfil.SERVIDOR) {
                return;
            }
            throw new AccessDeniedException("Padre só cria contas PADRE ou SERVIDOR");
        }
        if (perfilAlvo == Perfil.SERVIDOR && pastoraisPermissao.ehCoordenadorDeAlgumaPastoral()) {
            return;
        }
        throw new AccessDeniedException("Você não tem permissão para criar ou alterar uma conta com este perfil");
    }

    /** Para achar quem adicionar a uma pastoral: PADRE, ADMIN ou coordenador de alguma pastoral. */
    @Transactional(readOnly = true)
    public Page<UsuarioResumoDTO> buscarResumo(Pageable pageable) {
        if (!pastoraisPermissao.ehAdmin() && !pastoraisPermissao.ehPadre() && !pastoraisPermissao.ehCoordenadorDeAlgumaPastoral()) {
            throw new AccessDeniedException("Você não tem permissão para buscar usuários");
        }
        return repository.findByParoquiaIdAndActiveTrue(paroquiaId(), pageable)
                .map(u -> new UsuarioResumoDTO(u.getId(), u.getNome(), u.getEmail()));
    }

    @Transactional
    public UsuarioResponseDTO atualizar(Long id, UsuarioUpdateDTO request) {
        Usuario usuario = obterAtivo(id);
        String emailAntigo = usuario.getEmail();
        Perfil perfilAntigo = usuario.getPerfil();

        mapper.updateEntity(request, usuario);
        boolean senhaTrocada = request.senha() != null && !request.senha().isBlank();
        if (senhaTrocada) {
            usuario.setSenha(passwordEncoder.encode(request.senha()));
        }
        validar(usuario);
        Usuario salvo = repository.save(usuario);

        if (senhaTrocada || perfilAntigo != salvo.getPerfil() || !emailAntigo.equalsIgnoreCase(salvo.getEmail())) {
            sessaoService.encerrarTodas(emailAntigo);
        }
        return paraResposta(salvo);
    }

    @Override
    @Transactional
    public void desativar(Long id) {
        Usuario usuario = obterAtivo(id);
        usuario.setActive(false);
        repository.save(usuario);
        sessaoService.encerrarTodas(usuario.getEmail());
    }
}
