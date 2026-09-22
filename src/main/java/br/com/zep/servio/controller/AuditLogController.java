package br.com.zep.servio.controller;

import br.com.zep.servio.model.dto.AuditLogResponseDTO;
import br.com.zep.servio.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService service;

    @GetMapping
    public List<AuditLogResponseDTO> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public AuditLogResponseDTO buscar(@PathVariable Long id) {
        return service.buscar(id);
    }
}
