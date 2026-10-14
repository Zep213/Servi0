package br.com.zep.servio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Envio de e-mail sai numa thread própria, depois do commit. Executor pequeno e com nome:
 * SMTP lento não pode travar o pedido do coordenador nem tomar as threads do Tomcat.
 * Agendamento fica ligado aqui; os jobs se desligam sozinhos com servio.jobs.enabled=false.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    public static final String EXECUTOR_EMAIL = "emailExecutor";

    @Bean(name = EXECUTOR_EMAIL)
    public ThreadPoolTaskExecutor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("email-");
        executor.initialize();
        return executor;
    }
}
