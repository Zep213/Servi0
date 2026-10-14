package br.com.zep.servio.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Relógio único da aplicação (Etapa 6, Parte 1): todo "agora" de escalação, convite e jobs
 * passa por aqui, nunca por LocalDateTime.now() direto — os testes trocam por Clock.fixed.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${servio.fuso:America/Fortaleza}") String fuso) {
        return Clock.system(ZoneId.of(fuso));
    }
}
