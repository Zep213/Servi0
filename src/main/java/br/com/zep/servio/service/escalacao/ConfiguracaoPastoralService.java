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
    public static final String CHAVE_COBERTURA_AUTOMATICA = "COBERTURA_AUTOMATICA";
    public static final String CHAVE_LEMBRETE_RESPOSTA = "LEMBRETE_RESPOSTA";
    public static final String CHAVE_LEMBRETE_SERVICO = "LEMBRETE_SERVICO";
    private static final int PRAZO_RESPOSTA_PADRAO_HORAS = 24;
    private static final int LEMBRETE_RESPOSTA_PADRAO_HORAS_ANTES_DO_PRAZO = 6;
    private static final int LEMBRETE_SERVICO_PADRAO_HORAS_ANTES = 24;

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
        return numeroParametro(pastoralId, CHAVE_PRAZO_RESPOSTA, "horas", PRAZO_RESPOSTA_PADRAO_HORAS);
    }

    /**
     * Cobertura automática de vagas por modelo (Parte 3.3): ao contrário das regras de
     * elegibilidade e do PRAZO_RESPOSTA, o padrão aqui é DESLIGADA — só liga quem configurou
     * explicitamente uma linha ativa em pastoral_config.
     */
    public boolean coberturaAutomaticaLigada(Long pastoralId) {
        return configuracao(pastoralId, CHAVE_COBERTURA_AUTOMATICA).map(PastoralConfig::isAtiva).orElse(false);
    }

    /** Quantas horas antes do prazo do convite o lembrete de resposta é mandado (padrão: 6h). */
    public long lembreteRespostaHorasAntesDoPrazo(Long pastoralId) {
        return numeroParametro(pastoralId, CHAVE_LEMBRETE_RESPOSTA, "horasAntesDoPrazo",
                LEMBRETE_RESPOSTA_PADRAO_HORAS_ANTES_DO_PRAZO);
    }

    /** Quantas horas antes da celebração o lembrete de serviço (quem já aceitou) é mandado (padrão: 24h). */
    public long lembreteServicoHorasAntes(Long pastoralId) {
        return numeroParametro(pastoralId, CHAVE_LEMBRETE_SERVICO, "horasAntes",
                LEMBRETE_SERVICO_PADRAO_HORAS_ANTES);
    }

    private long numeroParametro(Long pastoralId, String chave, String parametro, int padrao) {
        return configuracao(pastoralId, chave)
                .map(PastoralConfig::getParametros)
                .map(p -> p.get(parametro))
                .map(v -> v instanceof Number n ? n.longValue() : Long.parseLong(v.toString()))
                .orElse((long) padrao);
    }

    private Optional<PastoralConfig> configuracao(Long pastoralId, String chave) {
        return pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(pastoralId, chave);
    }
}
