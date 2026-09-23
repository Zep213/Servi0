package br.com.zep.servio.service.escalacao.regra;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Vaga;

/**
 * A vaga para a qual o candidato está sendo avaliado, e a celebração que ela pertence.
 * alocacaoIgnoradaId é a própria alocação sendo editada (0 quando é uma criação), para
 * as regras não se bloquearem contra o registro que estão validando.
 */
public record ContextoElegibilidade(Vaga vaga, Long alocacaoIgnoradaId) {

    public Celebracao celebracao() {
        return vaga.getCelebracao();
    }
}
