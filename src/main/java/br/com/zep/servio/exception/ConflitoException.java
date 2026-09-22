package br.com.zep.servio.exception;

import org.springframework.http.HttpStatus;

public class ConflitoException extends ServioException {

    public ConflitoException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
