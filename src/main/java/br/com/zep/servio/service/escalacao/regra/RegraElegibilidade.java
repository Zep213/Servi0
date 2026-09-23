package br.com.zep.servio.service.escalacao.regra;

import br.com.zep.servio.model.Usuario;

import java.util.Map;
import java.util.Optional;

/**
 * Uma regra de elegibilidade para escalação. Cada implementação é um @Component
 * próprio: o Spring injeta List<RegraElegibilidade> automaticamente, então criar
 * uma regra nova é criar uma classe nova, sem tocar em mais nada.
 * Nenhuma regra pode assumir valores fixos: os parâmetros de cada pastoral vêm
 * de pastoral_config, com padroes() servindo de valor default.
 */
public interface RegraElegibilidade {

    /** Identificador estável, usado como chave em pastoral_config. */
    String codigo();

    String descricao();

    /** Valores default aplicados quando a pastoral não configurou nada para esta regra. */
    Map<String, Object> padroes();

    /** Vazio se o candidato pode ser escalado; presente com o motivo do impedimento, senão. */
    Optional<String> impedimento(Usuario candidato, ContextoElegibilidade contexto, Map<String, Object> parametros);
}
