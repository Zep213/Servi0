package br.com.zep.servio.config;

import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.AuditoriaAdminAssumidoFilter;
import br.com.zep.servio.security.LoginHandlers;
import br.com.zep.servio.security.LoginRateLimitFilter;
import br.com.zep.servio.security.TentativasLogin;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.session.autoconfigure.DefaultCookieSerializerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Cadastros com a mesma regra: leitura para qualquer logado, escrita para ADMIN/PADRE. */
    private static final String[] CADASTROS = {
            "/api/pastorais", "/api/pastorais/*", "/api/comunidades/**", "/api/funcoes/**",
            "/api/usuarios-funcoes/**", "/api/celebracoes/**", "/api/vagas/**"
    };

    /**
     * Recursos onde o gate fino é por papel DENTRO da pastoral (PastoraisPermissao), não pelo
     * Perfil global do usuário: o gate aqui só exige login, quem decide é o service/@PreAuthorize.
     */
    private static final String[] GESTAO_POR_PASTORAL = {
            "/api/pastorais/*/config/**", "/api/pastorais/*/financeiro/**", "/api/pastorais/*/reunioes/**",
            "/api/alocacoes/**", "/api/usuarios-pastorais/**"
    };

    private final TentativasLogin tentativasLogin;
    private final LoginHandlers loginHandlers;
    private final UsuarioLogado usuarioLogado;
    private final AuditLogService auditLogService;
    private final UsuarioRepository usuarioRepository;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * server.servlet.session.cookie.* só vale para a sessão nativa do container;
     * com Spring Session (spring-boot-session), o cookie é configurado por este customizer.
     */
    @Bean
    public DefaultCookieSerializerCustomizer cookieSerializerCustomizer(
            @Value("${server.servlet.session.cookie.name}") String nome,
            @Value("${server.servlet.session.cookie.http-only}") boolean httpOnly,
            @Value("${server.servlet.session.cookie.secure}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site}") String sameSite) {
        return serializer -> {
            serializer.setCookieName(nome);
            serializer.setUseHttpOnlyCookie(httpOnly);
            serializer.setUseSecureCookie(secure);
            serializer.setSameSite(sameSite);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.spa())
                .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
                .formLogin(f -> f
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .successHandler(loginHandlers::sucesso)
                        .failureHandler(loginHandlers::falha)
                        .permitAll())
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                        .deleteCookies("SERVIO_SESSION"))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(a -> a
                        // públicas
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers("/api/confirmacoes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()

                        // qualquer usuário logado
                        .requestMatchers("/api/me/**").authenticated()
                        .requestMatchers("/api/indisponibilidades/**", "/api/pedidos-troca/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/regras/**").authenticated()
                        // alterações pendentes do vice: quem pode confirmar/desfazer é checado no service
                        .requestMatchers("/api/alteracoes-pendentes/**").authenticated()

                        // plataforma: só ADMIN, sem filtro de tenant (2.2)
                        .requestMatchers("/api/plataforma/**").hasRole("ADMIN")

                        // usuários (a primeira regra que casar vence: mais específica antes da geral).
                        // Busca resumida e criação têm regra fina no service (perfil-alvo pedido,
                        // coordenador de pastoral só cria SERVIDOR); editar/desativar e a listagem
                        // completa são só PADRE/ADMIN (2.4).
                        .requestMatchers(HttpMethod.GET, "/api/usuarios/busca").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/usuarios").authenticated()
                        .requestMatchers("/api/usuarios/**").hasAnyRole("ADMIN", "PADRE")

                        // hierarquia por pastoral: papel dentro da pastoral quem decide (PastoraisPermissao
                        // no service/@PreAuthorize), não o Perfil global — inclui o convidado respondendo
                        // à própria escalação, cuja checagem de dono também é no service
                        .requestMatchers(GESTAO_POR_PASTORAL).authenticated()

                        // cadastros da escala
                        .requestMatchers(HttpMethod.GET, CADASTROS).authenticated()
                        .requestMatchers(CADASTROS).hasAnyRole("ADMIN", "PADRE")

                        // paróquia
                        .requestMatchers(HttpMethod.GET, "/api/paroquias/**").authenticated()
                        .requestMatchers("/api/paroquias/**").hasRole("ADMIN")

                        // agenda do padre: ADMIN também escreve (2.2: "pode ver e alterar tudo")
                        .requestMatchers("/api/compromissos-agenda/**").hasAnyRole("PADRE", "ADMIN")

                        // auditoria: leitura apenas; quem grava é o próprio sistema
                        .requestMatchers(HttpMethod.GET, "/api/audit-logs/**").hasAnyRole("ADMIN", "PADRE")

                        // rota nova nasce bloqueada até ser liberada de propósito
                        .anyRequest().denyAll())
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny))
                .addFilterBefore(new LoginRateLimitFilter(tentativasLogin), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new AuditoriaAdminAssumidoFilter(usuarioLogado, auditLogService, usuarioRepository),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
