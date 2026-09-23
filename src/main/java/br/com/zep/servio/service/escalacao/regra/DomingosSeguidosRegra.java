package br.com.zep.servio.service.escalacao.regra;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.AlocacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** Não servir em duas missas dominicais seguidas. Pode ser desligada por pastoral via ativa=false. */
@Component
@RequiredArgsConstructor
public class DomingosSeguidosRegra implements RegraElegibilidade {

    private final AlocacaoRepository alocacaoRepository;

    @Override
    public String codigo() {
        return "DOMINGOS_SEGUIDOS";
    }

    @Override
    public String descricao() {
        return "Bloqueia servir em dois domingos seguidos";
    }

    @Override
    public Map<String, Object> padroes() {
        return Map.of();
    }

    @Override
    public Optional<String> impedimento(Usuario candidato, ContextoElegibilidade contexto, Map<String, Object> parametros) {
        LocalDate dataVaga = contexto.celebracao().getData();
        if (dataVaga.getDayOfWeek() != DayOfWeek.SUNDAY) {
            return Optional.empty();
        }
        LocalDate domingoAnterior = dataVaga.minusDays(7);
        LocalDate domingoSeguinte = dataVaga.plusDays(7);

        for (Alocacao alocacao : alocacaoRepository.findByUsuarioIdAndActiveTrue(candidato.getId())) {
            LocalDate dataExistente = alocacao.getVaga().getCelebracao().getData();
            if (dataExistente.equals(domingoAnterior) || dataExistente.equals(domingoSeguinte)) {
                return Optional.of("Candidato já está escalado no domingo seguido");
            }
        }
        return Optional.empty();
    }
}
