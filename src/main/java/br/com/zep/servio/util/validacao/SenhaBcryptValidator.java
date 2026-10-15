package br.com.zep.servio.util.validacao;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class SenhaBcryptValidator implements ConstraintValidator<SenhaBcrypt, String> {

    static final int MIN_CARACTERES = 8;
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String senha, ConstraintValidatorContext contexto) {
        if (senha == null) {
            return true;
        }
        return senha.length() >= MIN_CARACTERES && senha.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
