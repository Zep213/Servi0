package br.com.zep.servio.exception;

import org.springframework.http.HttpStatus;

public class RecursoNaoEncontradoException extends ServioException {

    public RecursoNaoEncontradoException(String recurso, Long id) {
        super(recurso + " não encontrado(a) com id " + id, HttpStatus.NOT_FOUND);
    }
}
