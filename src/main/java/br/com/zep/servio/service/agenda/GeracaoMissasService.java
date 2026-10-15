package br.com.zep.servio.service.agenda;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.HorarioMissa;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.service.CoberturaAutomaticaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera as missas de um horário fixo (Sprint 4, cartão 2): uma {@code MISSA_DOMINICAL} para cada data do dia da semana
 * do horário, de agora até {@code servio.agenda.semanas-a-frente} semanas à frente, que ainda não exista. Idempotente:
 * o índice único {@code (horario_missa_id, data)} não olha {@code is_active}, então a missa que o padre desativou
 * (cancelou) nunca é recriada. A cobertura automática dá as vagas só a quem cobre o horário.
 */
@Service
public class GeracaoMissasService {

    private final CelebracaoRepository celebracaoRepository;
    private final CoberturaAutomaticaService coberturaAutomaticaService;
    private final Clock clock;
    private final int semanasAFrente;

    public GeracaoMissasService(CelebracaoRepository celebracaoRepository,
                                CoberturaAutomaticaService coberturaAutomaticaService, Clock clock,
                                @Value("${servio.agenda.semanas-a-frente:8}") int semanasAFrente) {
        this.celebracaoRepository = celebracaoRepository;
        this.coberturaAutomaticaService = coberturaAutomaticaService;
        this.clock = clock;
        this.semanasAFrente = semanasAFrente;
    }

    /** Cria as missas que faltam do horário e devolve quantas criou. Horário ou comunidade inativos não geram. */
    @Transactional
    public int gerar(HorarioMissa horario) {
        if (!horario.isActive() || !horario.getComunidade().isActive()) {
            return 0;
        }
        int criadas = 0;
        for (LocalDate data : datasAGerar(horario.diaDaSemana(), horario.getHora(),
                LocalDateTime.now(clock), semanasAFrente)) {
            if (celebracaoRepository.existsByHorarioMissaIdAndData(horario.getId(), data)) {
                continue;     // inclusive a desativada: missa cancelada não volta
            }
            Celebracao celebracao = new Celebracao();
            celebracao.setComunidade(horario.getComunidade());
            celebracao.setData(data);
            celebracao.setHora(horario.getHora());
            celebracao.setTipo(TipoCelebracao.MISSA_DOMINICAL);
            celebracao.setHorarioMissa(horario);
            celebracao.setParoquiaId(horario.getParoquiaId());
            celebracaoRepository.save(celebracao);
            coberturaAutomaticaService.aplicarNaCriacao(celebracao, horario.getParoquiaId());
            criadas++;
        }
        return criadas;
    }

    /**
     * Datas do dia da semana de hoje até hoje + N semanas (inclusive). Hoje só entra se a hora da missa ainda não
     * passou: nunca se cria missa no passado.
     */
    static List<LocalDate> datasAGerar(DayOfWeek dia, LocalTime hora, LocalDateTime agora, int semanasAFrente) {
        LocalDate fim = agora.toLocalDate().plusWeeks(semanasAFrente);
        List<LocalDate> datas = new ArrayList<>();
        for (LocalDate data = agora.toLocalDate().with(TemporalAdjusters.nextOrSame(dia));
             !data.isAfter(fim); data = data.plusWeeks(1)) {
            if (data.atTime(hora).isAfter(agora)) {
                datas.add(data);
            }
        }
        return datas;
    }
}
