package br.com.zep.servio.repository;

import br.com.zep.servio.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUsuarioId(Long usuarioId);

    List<AuditLog> findByTipoEventoAndReferenciaId(String tipoEvento, Long referenciaId);

    List<AuditLog> findByParoquiaId(Long paroquiaId);
}
