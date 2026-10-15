package br.com.zep.servio.util;

import java.util.Optional;

/** Telefone brasileiro: normaliza para +55DDNNNNNNNNN (fixo 10 dígitos, celular 11). Sem Spring. */
public final class Telefone {

    private Telefone() {
    }

    public static Optional<String> normalizar(String texto) {
        if (texto == null) {
            return Optional.empty();
        }
        String digitos = texto.replaceAll("[^0-9]", "");
        // um zero de tronco ("0 83 ...") fica de fora; zero a mais é DDD inválido
        digitos = digitos.replaceFirst("^0", "");
        if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() != 10 && digitos.length() != 11) {
            return Optional.empty();
        }
        if (digitos.charAt(0) == '0') {                       // DDD não começa com 0
            return Optional.empty();
        }
        if (digitos.length() == 11 && digitos.charAt(2) != '9') {   // celular tem 9 depois do DDD
            return Optional.empty();
        }
        return Optional.of("+55" + digitos);
    }

    /** Login digitado é telefone (e não e-mail): sem {@code @} e com pelo menos 10 dígitos. */
    public static boolean ehTelefone(String login) {
        if (login == null || login.contains("@")) {
            return false;
        }
        return login.chars().filter(Character::isDigit).count() >= 10;
    }
}
