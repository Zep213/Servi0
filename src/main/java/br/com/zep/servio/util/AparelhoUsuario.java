package br.com.zep.servio.util;

/**
 * Tipo de aparelho a partir do User-Agent, só para o aviso "sua senha foi alterada". Devolve um texto curto
 * ("Celular iPhone", "Computador Windows"...) e não guarda nem expõe o User-Agent, o IP nem a localização:
 * decisão do projeto (cidade exigiria serviço externo de IP). Sem Spring e sem biblioteca de User-Agent.
 */
public final class AparelhoUsuario {

    public static final String DESCONHECIDO = "aparelho desconhecido";

    private AparelhoUsuario() {
    }

    public static String descrever(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DESCONHECIDO;
        }
        if (contem(userAgent, "iPhone") || contem(userAgent, "iPod")) {
            return "Celular iPhone";
        }
        if (contem(userAgent, "iPad")) {
            return "Tablet";
        }
        if (contem(userAgent, "Android")) {
            // celular Android traz "Mobile"; tablet Android não
            return contem(userAgent, "Mobile") ? "Celular Android" : "Tablet";
        }
        if (contem(userAgent, "Windows")) {
            return "Computador Windows";
        }
        if (contem(userAgent, "Macintosh") || contem(userAgent, "Mac OS X")) {
            return "Computador Mac";
        }
        if (contem(userAgent, "CrOS") || contem(userAgent, "Linux") || contem(userAgent, "X11")) {
            return "Computador Linux";
        }
        return DESCONHECIDO;
    }

    private static boolean contem(String texto, String trecho) {
        return texto.contains(trecho);
    }
}
