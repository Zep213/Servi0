package br.com.zep.servio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Dois motores separados (HTML e texto) para o mesmo nome de template: {@code email/convite}
 * vira {@code email/convite.html} e {@code email/convite.txt}. Motor próprio, e não o da web,
 * para o e-mail não depender de view resolver nem de contexto de requisição.
 */
@Configuration
public class EmailTemplateConfig {

    private static final String PREFIXO = "templates/";

    @Bean
    public TemplateEngine emailHtmlEngine() {
        return motor(TemplateMode.HTML, ".html");
    }

    @Bean
    public TemplateEngine emailTextEngine() {
        return motor(TemplateMode.TEXT, ".txt");
    }

    private static TemplateEngine motor(TemplateMode modo, String sufixo) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(PREFIXO);
        resolver.setSuffix(sufixo);
        resolver.setTemplateMode(modo);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
