package br.com.zep.servio.util.validacao;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Senha nova: pelo menos 8 caracteres e no máximo 72 BYTES em UTF-8 (o limite real do BCrypt; com acentos,
 * 72 caracteres passariam de 72 bytes). Nulo é com {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = SenhaBcryptValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SenhaBcrypt {

    String message() default "A senha deve ter pelo menos 8 caracteres e no máximo 72 bytes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
