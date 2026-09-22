package br.com.zep.servio.exception;

import org.springframework.http.HttpStatus;

public class ServioException extends RuntimeException {

    private final HttpStatus status;

    public ServioException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
