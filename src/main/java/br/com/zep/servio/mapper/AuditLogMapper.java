package br.com.zep.servio.mapper;

import br.com.zep.servio.model.AuditLog;
import br.com.zep.servio.model.dto.AuditLogResponseDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    @Mapping(source = "usuario.id", target = "usuarioId")
    AuditLogResponseDTO toResponse(AuditLog entity);
}
