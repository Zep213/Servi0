package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.mapper.AuditLogMapper;
import br.com.zep.servio.model.AuditLog;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.AuditLogResponseDTO;
import br.com.zep.servio.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repository;
    private final AuditLogMapper mapper;

    @Transactional(readOnly = true)
    public List<AuditLogResponseDTO> listar() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AuditLogResponseDTO buscar(Long id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new RecursoNaoEncontradoException("AuditLog", id));
    }

    /** Registra um evento de auditoria; usado por outros services. */
    @Transactional
    public void registrar(String tipoEvento, Long referenciaId, Usuario usuario) {
        AuditLog log = new AuditLog();
        log.setTipoEvento(tipoEvento);
        log.setReferenciaId(referenciaId);
        log.setUsuario(usuario);
        repository.save(log);
    }
}
