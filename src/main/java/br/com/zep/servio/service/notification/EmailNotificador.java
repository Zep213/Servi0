package br.com.zep.servio.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificador implements Notificador {

    private final ObjectProvider<JavaMailSender> mailSender;

    @Override
    public void notificar(String destinatario, String assunto, String mensagem) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("spring.mail.host não configurado; e-mail para {} não enviado", destinatario);
            return;
        }
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(destinatario);
        email.setSubject(assunto);
        email.setText(mensagem);
        sender.send(email);
    }
}
