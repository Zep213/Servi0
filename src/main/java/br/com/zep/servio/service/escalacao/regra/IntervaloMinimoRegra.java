package br.com.zep.servio.service.escalacao.regra;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

/** Intervalo mínimo entre dois serviços do mesmo candidato. O número de horas vem do parâmetro da pastoral. */
@Component
@RequiredArgsConstructor
public class IntervaloMinimoRegra implements RegraElegibilidade {

    private static final String PARAM_HORAS = "horas";
    private static final long PADRAO_HORAS = 48;

    private final AlocacaoRepository alocacaoRepository;

    @Override
    public String codigo() {
        return "INTERVALO_MINIMO";
    }

    @Override
    public String descricao() {
        return "Intervalo mínimo entre dois serviços do mesmo candidato";
    }

    @Override
    public Map<String, Object> padroes() {
        return Map.of(PARAM_HORAS, PADRAO_HORAS);
    }

    @Override
    public Optional<String> impedimento(Usuario candidato, ContextoElegibilidade contexto, Map<String, Object> parametros) {
        long horasMinimas = numero(parametros.getOrDefault(PARAM_HORAS, PADRAO_HORAS));
        LocalDateTime dataHoraVaga = LocalDateTime.of(contexto.celebracao().getData(), contexto.celebracao().getHora());

        for (Alocacao alocacao : alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                candidato.getId(), StatusConvite.OCUPANTES, contexto.alocacaoIgnoradaId())) {
            var celebracaoExistente = alocacao.getVaga().getCelebracao();
            LocalDateTime dataHoraExistente = LocalDateTime.of(celebracaoExistente.getData(), celebracaoExistente.getHora());
            long horasEntre = Math.abs(ChronoUnit.HOURS.between(dataHoraExistente, dataHoraVaga));
            if (horasEntre < horasMinimas) {
                return Optional.of("Intervalo mínimo de " + horasMinimas + "h entre serviços não respeitado");
            }
        }
        return Optional.empty();
    }

    private long numero(Object valor) {
        return valor instanceof Number n ? n.longValue() : Long.parseLong(valor.toString());
    }
}
