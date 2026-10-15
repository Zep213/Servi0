package br.com.zep.servio.service;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.model.CoberturaMissa;
import br.com.zep.servio.model.HorarioMissa;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.HorarioMissaDTOs.CoberturaMissaItemDTO;
import br.com.zep.servio.repository.CoberturaMissaRepository;
import br.com.zep.servio.repository.HorarioMissaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Qual horário de missa cada pastoral cobre (a Pascom cobre uma por domingo em cada comunidade). Ler: quem vê a
 * pastoral. Alterar: coordenador da pastoral, padre ou ADMIN; pastoral fora do alcance dá 404.
 */
@Service
@RequiredArgsConstructor
public class CoberturaMissaService {

    private final CoberturaMissaRepository repository;
    private final HorarioMissaRepository horarioRepository;
    private final PastoralRepository pastoralRepository;
    private final UsuarioRepository usuarioRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<CoberturaMissaItemDTO> listar(Long pastoralId) {
        pastoral(pastoralId);
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException("Pastoral", pastoralId);
        }
        return montar(pastoralId);
    }

    /** Troca o conjunto de horários cobertos pela pastoral. No máximo um por comunidade. */
    @Transactional
    public List<CoberturaMissaItemDTO> salvar(Long pastoralId, List<Long> horarioMissaIds) {
        Pastoral pastoral = pastoral(pastoralId);
        exigirGestor(pastoralId);

        Set<Long> pedidos = new LinkedHashSet<>(horarioMissaIds);
        List<HorarioMissa> horarios = pedidos.isEmpty() ? List.of()
                : horarioRepository.findByParoquiaIdAndIdInAndActiveTrue(usuarioLogado.paroquiaId(), pedidos);
        if (horarios.size() != pedidos.size()) {
            Long faltando = pedidos.stream()
                    .filter(id -> horarios.stream().noneMatch(h -> h.getId().equals(id))).findFirst().orElseThrow();
            throw new RecursoNaoEncontradoException("HorarioMissa", faltando);
        }
        Set<Long> comunidades = new HashSet<>();
        for (HorarioMissa horario : horarios) {
            if (!comunidades.add(horario.getComunidade().getId())) {
                throw new RegraNegocioException("Escolha um horário por comunidade");
            }
        }

        Set<Long> jaCobertos = new HashSet<>();
        for (CoberturaMissa existente : repository.findByPastoralIdAndActiveTrue(pastoralId)) {
            Long horarioId = existente.getHorarioMissa().getId();
            if (pedidos.contains(horarioId)) {
                jaCobertos.add(horarioId);
            } else {
                existente.setActive(false);
                repository.save(existente);
            }
        }
        for (HorarioMissa horario : horarios) {
            if (!jaCobertos.contains(horario.getId())) {
                CoberturaMissa nova = new CoberturaMissa();
                nova.setPastoral(pastoral);
                nova.setHorarioMissa(horario);
                nova.setParoquiaId(usuarioLogado.paroquiaId());
                repository.save(nova);
            }
        }
        Usuario autor = usuarioRepository.findById(usuarioLogado.id()).orElseThrow();
        auditLogService.registrar("COBERTURA_MISSA_ALTERADA", pastoralId, autor, usuarioLogado.paroquiaId(),
                "Horários cobertos: " + pedidos);
        return montar(pastoralId);
    }

    private List<CoberturaMissaItemDTO> montar(Long pastoralId) {
        Set<Long> cobertos = new HashSet<>();
        repository.findByPastoralIdAndActiveTrue(pastoralId).forEach(c -> cobertos.add(c.getHorarioMissa().getId()));
        return horarioRepository.findByParoquiaIdAndActiveTrueOrderByComunidadeIdAscDiaSemanaAscHoraAsc(
                        usuarioLogado.paroquiaId()).stream()
                .map(h -> new CoberturaMissaItemDTO(h.getId(), h.getComunidade().getId(), h.getComunidade().getNome(),
                        h.getDiaSemana(), h.getHora(), cobertos.contains(h.getId())))
                .toList();
    }

    private void exigirGestor(Long pastoralId) {
        if (!pastoraisPermissao.visivel(pastoralId)) {
            throw new RecursoNaoEncontradoException("Pastoral", pastoralId);
        }
        if (!pastoraisPermissao.temPapel(pastoralId, "COORDENADOR")) {
            throw new AccessDeniedException("Só o coordenador da pastoral (ou padre/ADMIN) escolhe os horários de missa");
        }
    }

    private Pastoral pastoral(Long pastoralId) {
        return pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pastoral", pastoralId));
    }
}
