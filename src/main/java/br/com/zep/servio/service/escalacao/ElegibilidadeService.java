package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.service.escalacao.regra.ContextoElegibilidade;
import br.com.zep.servio.service.escalacao.regra.RegraElegibilidade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Aplica o catálogo de regras, já resolvido com a configuração da pastoral, a um candidato/vaga. */
@Service
@RequiredArgsConstructor
public class ElegibilidadeService {

    private final ConfiguracaoPastoralService configuracaoPastoralService;

    /**
     * Motivos de impedimento do candidato para a vaga; lista vazia = candidato elegível.
     * alocacaoIgnoradaId: a própria alocação sendo editada (0 numa criação), para as
     * regras não considerarem o registro que está sendo validado como um conflito consigo mesmo.
     */
    public List<String> impedimentos(Usuario candidato, Vaga vaga, Long alocacaoIgnoradaId) {
        Long pastoralId = vaga.getFuncao().getPastoral().getId();
        ContextoElegibilidade contexto = new ContextoElegibilidade(vaga, alocacaoIgnoradaId);

        List<String> impedimentos = new ArrayList<>();
        for (RegraElegibilidade regra : configuracaoPastoralService.catalogo()) {
            if (!configuracaoPastoralService.ativa(pastoralId, regra.codigo())) {
                continue;
            }
            Map<String, Object> parametros = configuracaoPastoralService.parametros(pastoralId, regra);
            regra.impedimento(candidato, contexto, parametros).ifPresent(impedimentos::add);
        }
        return impedimentos;
    }
}
