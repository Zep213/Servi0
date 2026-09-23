package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.model.PastoralConfig;
import br.com.zep.servio.repository.PastoralConfigRepository;
import br.com.zep.servio.service.escalacao.regra.RegraElegibilidade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Junta o catálogo de regras (List<RegraElegibilidade>, injetado pelo Spring) com o que
 * cada pastoral configurou em pastoral_config, aplicando os valores padrão quando a
 * pastoral não configurou nada. Nenhuma regra ou prazo fica fixo: tudo que não está em
 * pastoral_config cai no padrão de cada regra (ou, para PRAZO_RESPOSTA, no padrão daqui).
 */
@Service
@RequiredArgsConstructor
public class ConfiguracaoPastoralService {

    public static final String CHAVE_PRAZO_RESPOSTA = "PRAZO_RESPOSTA";
    private static final int PRAZO_RESPOSTA_PADRAO_HORAS = 24;

    private final List<RegraElegibilidade> regras;
    private final PastoralConfigRepository pastoralConfigRepository;

    public List<RegraElegibilidade> catalogo() {
        return regras;
    }

    /** true se a pastoral não desativou explicitamente a regra. */
    public boolean ativa(Long pastoralId, String codigoRegra) {
        return configuracao(pastoralId, codigoRegra).map(PastoralConfig::isAtiva).orElse(true);
    }

    /** Parâmetros efetivos da regra para a pastoral: o que ela configurou, ou o padrão da regra. */
    public Map<String, Object> parametros(Long pastoralId, RegraElegibilidade regra) {
        return configuracao(pastoralId, regra.codigo())
                .map(PastoralConfig::getParametros)
                .orElseGet(regra::padroes);
    }

    /** Prazo de resposta do convite, em horas, para a pastoral (padrão: 24h). */
    public long prazoRespostaHoras(Long pastoralId) {
        return configuracao(pastoralId, CHAVE_PRAZO_RESPOSTA)
                .map(PastoralConfig::getParametros)
                .map(p -> p.get("horas"))
                .map(v -> v instanceof Number n ? n.longValue() : Long.parseLong(v.toString()))
                .orElse((long) PRAZO_RESPOSTA_PADRAO_HORAS);
    }

    private Optional<PastoralConfig> configuracao(Long pastoralId, String chave) {
        return pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(pastoralId, chave);
    }
}
