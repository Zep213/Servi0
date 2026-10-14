package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.exception.ServioException;
import org.springframework.http.HttpStatus;

/** Prazo do convite venceu: 410 na página pública. A expiração é gravada antes de lançar (noRollbackFor). */
public class ConviteExpiradoException extends ServioException {

    public ConviteExpiradoException() {
        super("Prazo de resposta deste convite expirou", HttpStatus.GONE);
    }
}
