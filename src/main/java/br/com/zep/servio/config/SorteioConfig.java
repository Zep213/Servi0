package br.com.zep.servio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/** Fonte de aleatoriedade do sorteio. Os testes trocam por um gerador com semente fixa. */
@Configuration
public class SorteioConfig {

    @Bean
    public RandomGenerator randomGenerator() {
        return new SecureRandom();
    }
}
