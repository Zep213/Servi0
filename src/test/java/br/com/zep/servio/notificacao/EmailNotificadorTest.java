package br.com.zep.servio.notificacao;

import br.com.zep.servio.config.EmailTemplateConfig;
import br.com.zep.servio.service.notification.EmailNotificador;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Monta a mensagem MIME de verdade (sem SMTP): texto e HTML alternativos, remetente e assunto. */
class EmailNotificadorTest {

    private final List<MimeMessage> enviadas = new ArrayList<>();

    private final JavaMailSenderImpl sender = new JavaMailSenderImpl() {
        @Override
        public void send(MimeMessage mensagem) {
            enviadas.add(mensagem);
        }
    };

    private EmailNotificador notificador() {
        EmailTemplateConfig config = new EmailTemplateConfig();
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("mailSender", sender));
        return new EmailNotificador(beans.getBeanProvider(JavaMailSender.class),
                config.emailHtmlEngine(), config.emailTextEngine(), "Servio <no-reply@servio.local>");
    }

    @Test
    void enviaConviteComTextoEHtml() throws Exception {
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("assunto", "Você foi escalada");
        variaveis.put("nome", "Maria");
        variaveis.put("titulo", "Missa dominical");
        variaveis.put("data", "11/10/2026");
        variaveis.put("hora", "08:00");
        variaveis.put("funcao", "Comunicação");
        variaveis.put("pastoral", "Pascom");
        variaveis.put("prazo", "09/10/2026 08:00");
        variaveis.put("link", "http://localhost/convite#token-de-teste");
        variaveis.put("chegada", null);
        variaveis.put("observacao", null);

        notificador().enviar("maria@exemplo.com", "Você foi escalada", "convite", variaveis);

        assertThat(enviadas).hasSize(1);
        MimeMessage mensagem = enviadas.getFirst();
        assertThat(mensagem.getSubject()).isEqualTo("Você foi escalada");
        assertThat(mensagem.getFrom()[0].toString()).contains("no-reply@servio.local");
        ByteArrayOutputStream bruto = new ByteArrayOutputStream();
        mensagem.writeTo(bruto);
        String conteudo = bruto.toString();
        assertThat(conteudo).contains("multipart/alternative", "text/plain", "text/html");
    }
}
