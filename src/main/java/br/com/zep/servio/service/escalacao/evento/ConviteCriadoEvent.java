package br.com.zep.servio.service.escalacao.evento;

import br.com.zep.servio.model.enumerated.TipoNotificacao;

/**
 * Convite emitido para uma alocação. O token puro viaja só em memória, até o e-mail sair
 * depois do commit. O toString esconde o token para ele não cair em log.
 */
public record ConviteCriadoEvent(Long alocacaoId, String token, TipoNotificacao tipo) {

    @Override
    public String toString() {
        return "ConviteCriadoEvent[alocacaoId=" + alocacaoId + ", tipo=" + tipo + ", token=***]";
    }
}
