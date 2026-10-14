package br.com.zep.servio.service.notification;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

@Slf4j
@Component
public class EmailNotificador implements Notificador {

    private final ObjectProvider<JavaMailSender> mailSender;
    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;
    private final String remetente;

    /**
     * Construtor escrito à mão: o Lombok não copia o @Qualifier para o parâmetro, e o Boot já
     * tem um TemplateEngine próprio (o da web), então a escolha precisa ser explícita.
     */
    public EmailNotificador(ObjectProvider<JavaMailSender> mailSender,
                            @Qualifier("emailHtmlEngine") TemplateEngine htmlEngine,
                            @Qualifier("emailTextEngine") TemplateEngine textEngine,
                            @Value("${servio.mail.from:Servio <no-reply@servio.local>}") String remetente) {
        this.mailSender = mailSender;
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine;
        this.remetente = remetente;
    }

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

    @Override
    public void enviar(String destinatario, String assunto, String template, Map<String, Object> variaveis) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("spring.mail.host não configurado; e-mail '{}' para {} não enviado", template, destinatario);
            return;
        }
        Context contexto = new Context();
        contexto.setVariables(variaveis);
        String texto = textEngine.process(template, contexto);
        String html = htmlEngine.process(template, contexto);
        try {
            MimeMessage mensagem = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensagem, true, "UTF-8");
            helper.setFrom(remetente);
            helper.setTo(destinatario);
            helper.setSubject(assunto);
            helper.setText(texto, html);
            sender.send(mensagem);
        } catch (Exception e) {
            // Falha de SMTP não pode desfazer o que já foi comitado: loga e segue
            throw new IllegalStateException("Falha ao enviar e-mail '" + template + "'", e);
        }
    }
}
