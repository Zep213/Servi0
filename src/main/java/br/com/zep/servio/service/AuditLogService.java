package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.mapper.AuditLogMapper;
import br.com.zep.servio.model.AuditLog;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.AuditLogResponseDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.AuditLogRepository;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;
    private final UsuarioLogado usuarioLogado;

    /** ADMIN vê a auditoria de todas as paróquias; qualquer outro perfil só a da própria. */
    @Transactional(readOnly = true)
    public List<AuditLogResponseDTO> listar() {
        List<AuditLog> logs = usuarioLogado.get().getPerfil() == Perfil.ADMIN
                ? repository.findAll()
                : repository.findByParoquiaId(usuarioLogado.paroquiaId());
        return logs.stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AuditLogResponseDTO buscar(Long id) {
        AuditLog log = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("AuditLog", id));
        boolean admin = usuarioLogado.get().getPerfil() == Perfil.ADMIN;
        if (!admin && !Objects.equals(log.getParoquiaId(), usuarioLogado.paroquiaId())) {
            throw new RecursoNaoEncontradoException("AuditLog", id);
        }
        return mapper.toResponse(log);
    }

    /** Registra um evento de auditoria; usado por outros services. */
    @Transactional
    public void registrar(String tipoEvento, Long referenciaId, Usuario usuario) {
        registrar(tipoEvento, referenciaId, usuario, null, null);
    }

    @Transactional
    public void registrar(String tipoEvento, Long referenciaId, Usuario usuario, Long paroquiaId, String detalhe) {
        AuditLog log = new AuditLog();
        log.setTipoEvento(tipoEvento);
        log.setReferenciaId(referenciaId);
        log.setUsuario(usuario);
        log.setParoquiaId(paroquiaId);
        log.setDetalhe(detalhe);
        repository.save(log);
    }
}
