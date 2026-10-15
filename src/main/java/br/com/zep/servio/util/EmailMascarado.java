package br.com.zep.servio.util;

/** E-mail com o miolo escondido, para respostas e auditoria (ex.: m***@gmail.com). Sem Spring. */
public final class EmailMascarado {

    private EmailMascarado() {
    }

    public static String mascarar(String email) {
        if (email == null) {
            return "****";
        }
        int arroba = email.indexOf('@');
        if (arroba < 1) {
            return "****";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
