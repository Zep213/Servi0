package br.com.zep.servio.exception;

import org.springframework.http.HttpStatus;

public class RegraNegocioException extends ServioException {

    public RegraNegocioException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
