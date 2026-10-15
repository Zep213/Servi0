package br.com.zep.servio.service.conta;

import br.com.zep.servio.exception.ServioException;
import org.springframework.http.HttpStatus;

/** O token existe, é do tipo certo e ainda não foi usado, mas o prazo passou: 410. */
public class TokenContaVencidoException extends ServioException {

    public TokenContaVencidoException() {
        super("Este link venceu. Peça um novo", HttpStatus.GONE);
    }
}
