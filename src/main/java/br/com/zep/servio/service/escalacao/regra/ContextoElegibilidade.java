package br.com.zep.servio.service.escalacao.regra;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Vaga;

/** A vaga para a qual o candidato está sendo avaliado, e a celebração que ela pertence. */
public record ContextoElegibilidade(Vaga vaga) {

    public Celebracao celebracao() {
        return vaga.getCelebracao();
    }
}
