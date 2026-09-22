package br.com.zep.servio.service.notification;

public interface Notificador {

    void notificar(String destinatario, String assunto, String mensagem);
}
