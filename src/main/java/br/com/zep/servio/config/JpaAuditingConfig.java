package br.com.zep.servio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "servioAuditingDateTimeProvider")
public class JpaAuditingConfig {

    /**
     * Sem isto, created_at/updated_at usam o relógio padrão da JVM, não o Clock
     * (America/Fortaleza) do resto do app — os dois podem divergir conforme o fuso do host.
     */
    @Bean
    public DateTimeProvider servioAuditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(LocalDateTime.now(clock));
    }
}
