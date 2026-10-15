package br.com.zep.servio.service;

import br.com.zep.servio.exception.ConflitoException;
import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.model.CoberturaMissa;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.HorarioMissa;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.HorarioMissaDTOs.HorarioMissaRequestDTO;
import br.com.zep.servio.model.dto.HorarioMissaDTOs.HorarioMissaResponseDTO;
import br.com.zep.servio.repository.CoberturaMissaRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.HorarioMissaRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Horários fixos de missa de uma comunidade (Sprint 4, cartão 1). Escrever é do PADRE/ADMIN; ler, de qualquer
 * pessoa logada da paróquia. Comunidade de outra paróquia dá 404.
 */
@Service
@RequiredArgsConstructor
public class HorarioMissaService {

    private final HorarioMissaRepository repository;
    private final CoberturaMissaRepository coberturaRepository;
    private final ComunidadeRepository comunidadeRepository;
    private final UsuarioRepository usuarioRepository;
    private final PastoraisPermissao pastoraisPermissao;
    private final UsuarioLogado usuarioLogado;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<HorarioMissaResponseDTO> listar(Long comunidadeId) {
        comunidade(comunidadeId);
        return repository.findByComunidadeIdAndActiveTrueOrderByDiaSemanaAscHoraAsc(comunidadeId).stream()
                .map(HorarioMissaService::paraResposta).toList();
    }

    @Transactional
    public HorarioMissaResponseDTO criar(Long comunidadeId, HorarioMissaRequestDTO request) {
        exigirPadreOuAdmin();
        Comunidade comunidade = comunidade(comunidadeId);
        HorarioMissa horario = new HorarioMissa();
        horario.setComunidade(comunidade);
        horario.setParoquiaId(usuarioLogado.paroquiaId());
        aplicar(horario, request);
        validarUnico(horario, 0L);
        HorarioMissa salvo = repository.save(horario);
        auditar("criado", salvo);
        return paraResposta(salvo);
    }

    @Transactional
    public HorarioMissaResponseDTO atualizar(Long comunidadeId, Long id, HorarioMissaRequestDTO request) {
        exigirPadreOuAdmin();
        comunidade(comunidadeId);
        HorarioMissa horario = obter(comunidadeId, id);
        aplicar(horario, request);
        validarUnico(horario, horario.getId());
        HorarioMissa salvo = repository.save(horario);
        auditar("alterado", salvo);
        return paraResposta(salvo);
    }

    /** Desativa o horário e as escolhas de cobertura que apontavam para ele. As celebrações já geradas ficam. */
    @Transactional
    public void desativar(Long comunidadeId, Long id) {
        exigirPadreOuAdmin();
        comunidade(comunidadeId);
        HorarioMissa horario = obter(comunidadeId, id);
        horario.setActive(false);
        repository.save(horario);
        for (CoberturaMissa cobertura : coberturaRepository.findByHorarioMissaIdAndActiveTrue(id)) {
            cobertura.setActive(false);
            coberturaRepository.save(cobertura);
        }
        auditar("desativado", horario);
    }

    private void aplicar(HorarioMissa horario, HorarioMissaRequestDTO request) {
        int dia = request.diaSemana() != null ? request.diaSemana() : DayOfWeek.SUNDAY.getValue();
        horario.setDiaSemana((short) dia);
        horario.setHora(request.hora());
    }

    private void validarUnico(HorarioMissa horario, Long idAtual) {
        LocalTime hora = horario.getHora();
        if (repository.existsByComunidadeIdAndDiaSemanaAndHoraAndActiveTrueAndIdNot(
                horario.getComunidade().getId(), horario.getDiaSemana(), hora, idAtual == null ? 0L : idAtual)) {
            throw new ConflitoException("Já existe este horário de missa nesta comunidade");
        }
    }

    private void exigirPadreOuAdmin() {
        if (!pastoraisPermissao.ehAdmin() && !pastoraisPermissao.ehPadre()) {
            throw new AccessDeniedException("Só padre ou admin gerencia os horários de missa");
        }
    }

    private Comunidade comunidade(Long comunidadeId) {
        return comunidadeRepository.findByIdAndParoquiaIdAndActiveTrue(comunidadeId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Comunidade", comunidadeId));
    }

    private HorarioMissa obter(Long comunidadeId, Long id) {
        return repository.findByIdAndComunidadeIdAndActiveTrue(id, comunidadeId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("HorarioMissa", id));
    }

    private void auditar(String acao, HorarioMissa horario) {
        Usuario autor = usuarioRepository.findById(usuarioLogado.id()).orElseThrow();
        auditLogService.registrar("HORARIO_MISSA_ALTERADO", horario.getId(), autor, horario.getParoquiaId(),
                "Horário " + acao + ": dia " + horario.getDiaSemana() + " às " + horario.getHora());
    }

    static HorarioMissaResponseDTO paraResposta(HorarioMissa horario) {
        return new HorarioMissaResponseDTO(horario.getId(), horario.getComunidade().getId(), horario.getDiaSemana(),
                horario.getHora());
    }
}
