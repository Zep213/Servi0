package br.com.zep.servio.service.notification;

import java.util.Map;

public interface Notificador {

    void notificar(String destinatario, String assunto, String mensagem);

    /**
     * E-mail HTML com versão em texto, a partir de {@code templates/email/{template}.html} e
     * {@code .txt}. As variáveis chegam já formatadas: o template não faz conta nem consulta.
     */
    void enviar(String destinatario, String assunto, String template, Map<String, Object> variaveis);
}
