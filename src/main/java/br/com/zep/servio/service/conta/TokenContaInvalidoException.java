package br.com.zep.servio.service.conta;

import br.com.zep.servio.exception.ServioException;
import org.springframework.http.HttpStatus;

/** Token inexistente, já usado, de outro tipo ou de conta inativa: sempre a mesma 404, sem dizer qual. */
public class TokenContaInvalidoException extends ServioException {

    public TokenContaInvalidoException() {
        super("Link não encontrado ou já usado", HttpStatus.NOT_FOUND);
    }
}
