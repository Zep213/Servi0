package br.com.zep.servio.service.escalacao.regra;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.IndisponibilidadeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** Candidato não pode ser escalado num dia em que registrou indisponibilidade. */
@Component
@RequiredArgsConstructor
public class SemIndisponibilidadeRegra implements RegraElegibilidade {

    private final IndisponibilidadeRepository indisponibilidadeRepository;

    @Override
    public String codigo() {
        return "SEM_INDISPONIBILIDADE";
    }

    @Override
    public String descricao() {
        return "Candidato não pode estar com indisponibilidade registrada na data da celebração";
    }

    @Override
    public Map<String, Object> padroes() {
        return Map.of();
    }

    @Override
    public Optional<String> impedimento(Usuario candidato, ContextoElegibilidade contexto, Map<String, Object> parametros) {
        var data = contexto.celebracao().getData();
        boolean indisponivel = !indisponibilidadeRepository
                .findByUsuarioIdAndDataInicioLessThanEqualAndDataFimGreaterThanEqualAndActiveTrue(candidato.getId(), data, data)
                .isEmpty();
        return indisponivel ? Optional.of("Candidato está indisponível nesta data") : Optional.empty();
    }
}
