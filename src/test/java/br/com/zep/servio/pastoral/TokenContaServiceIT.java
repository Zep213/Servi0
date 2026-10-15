package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.TokenConta;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.TipoTokenConta;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.TokenContaRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.conta.LimpezaTokensContaJob;
import br.com.zep.servio.service.conta.TokenContaInvalidoException;
import br.com.zep.servio.service.conta.TokenContaService;
import br.com.zep.servio.service.conta.TokenContaVencidoException;
import br.com.zep.servio.service.escalacao.TokenConvite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Token de conta (Sprint 2, Cartão 1). Relógio controlado, como no {@code JobsIT}; sem
 * {@code @Transactional}: o consumo precisa comitar de verdade para a corrida valer.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class TokenContaServiceIT {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");

    static final RelogioDeTeste RELOGIO = new RelogioDeTeste(Instant.now());

    @TestConfiguration
    static class ConfigRelogio {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return RELOGIO;
        }
    }

    static final class RelogioDeTeste extends Clock {
        private volatile Instant instante;

        RelogioDeTeste(Instant instante) {
            this.instante = instante;
        }

        @Override
        public ZoneId getZone() {
            return FUSO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }

    @Autowired
    ParoquiaRepository paroquiaRepository;
    @Autowired
    UsuarioRepository usuarioRepository;
    @Autowired
    PastoralRepository pastoralRepository;
    @Autowired
    FuncaoRepository funcaoRepository;
    @Autowired
    UsuarioPastoralRepository usuarioPastoralRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    TokenContaService service;
    @Autowired
    TokenContaRepository repository;
    @Autowired
    TokenConvite tokenConvite;
    @Autowired
    PlatformTransactionManager transacoes;

    CenarioParoquia cenario;
    Usuario pessoa;

    @BeforeEach
    void setUp() {
        RELOGIO.instante = Instant.now();
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        pessoa = cenario.membro1Pascom;
    }

    private void avancar(long quantidade, ChronoUnit unidade) {
        RELOGIO.instante = RELOGIO.instante.plus(quantidade, unidade);
    }

    @Test
    void emitirGuardaSoOHashEODevolvidoValeParaConsumir() {
        String token = service.emitir(pessoa, TipoTokenConta.ATIVACAO);

        TokenConta gravado = repository.buscarPorHash(tokenConvite.hash(token)).orElseThrow();
        assertThat(gravado.getTokenHash()).hasSize(64).isNotEqualTo(token);
        assertThat(gravado.getUsadoEm()).isNull();
        assertThat(service.consumir(token, TipoTokenConta.ATIVACAO).getId()).isEqualTo(pessoa.getId());
        assertThat(repository.buscarPorHash(tokenConvite.hash(token)).orElseThrow().getUsadoEm()).isNotNull();
    }

    @Test
    void segundoConsumoDa404() {
        String token = service.emitir(pessoa, TipoTokenConta.RECUPERACAO);
        service.consumir(token, TipoTokenConta.RECUPERACAO);

        assertThatThrownBy(() -> service.consumir(token, TipoTokenConta.RECUPERACAO))
                .isInstanceOf(TokenContaInvalidoException.class);
    }

    @Test
    void tokenDeOutroTipoEInexistenteDao404ESemGastarOToken() {
        String token = service.emitir(pessoa, TipoTokenConta.ATIVACAO);

        assertThatThrownBy(() -> service.consumir(token, TipoTokenConta.RECUPERACAO))
                .isInstanceOf(TokenContaInvalidoException.class);
        assertThatThrownBy(() -> service.consumir("token-que-nao-existe-1234567890abcdefghijk", TipoTokenConta.ATIVACAO))
                .isInstanceOf(TokenContaInvalidoException.class);
        // o tipo errado não queimou o token: o certo ainda funciona
        assertThat(service.consumir(token, TipoTokenConta.ATIVACAO).getId()).isEqualTo(pessoa.getId());
    }

    @Test
    void tokenDeUsuarioDesativadoDa404() {
        String token = service.emitir(pessoa, TipoTokenConta.ATIVACAO);
        Usuario desativado = usuarioRepository.findById(pessoa.getId()).orElseThrow();
        desativado.setActive(false);
        usuarioRepository.save(desativado);

        assertThatThrownBy(() -> service.consumir(token, TipoTokenConta.ATIVACAO))
                .isInstanceOf(TokenContaInvalidoException.class);
    }

    @Test
    void ativacaoVenceDepoisDeSeteDiasERecuperacaoDepoisDeUmaHora() {
        String ativacao = service.emitir(pessoa, TipoTokenConta.ATIVACAO);
        String recuperacao = service.emitir(pessoa, TipoTokenConta.RECUPERACAO);

        avancar(61, ChronoUnit.MINUTES);
        assertThatThrownBy(() -> service.consumir(recuperacao, TipoTokenConta.RECUPERACAO))
                .isInstanceOf(TokenContaVencidoException.class);
        // 61 minutos não vencem a ativação
        assertThat(service.consumir(ativacao, TipoTokenConta.ATIVACAO).getId()).isEqualTo(pessoa.getId());

        String outra = service.emitir(pessoa, TipoTokenConta.ATIVACAO);
        avancar(7 * 24 * 60 + 1, ChronoUnit.MINUTES);
        assertThatThrownBy(() -> service.consumir(outra, TipoTokenConta.ATIVACAO))
                .isInstanceOf(TokenContaVencidoException.class);
    }

    @Test
    void novoEmitirDoMesmoTipoMataOAnteriorETiposDiferentesConvivem() {
        String primeiro = service.emitir(pessoa, TipoTokenConta.RECUPERACAO);
        String ativacao = service.emitir(pessoa, TipoTokenConta.ATIVACAO);
        String segundo = service.emitir(pessoa, TipoTokenConta.RECUPERACAO);

        assertThatThrownBy(() -> service.consumir(primeiro, TipoTokenConta.RECUPERACAO))
                .isInstanceOf(TokenContaInvalidoException.class);
        assertThat(service.consumir(segundo, TipoTokenConta.RECUPERACAO).getId()).isEqualTo(pessoa.getId());
        assertThat(service.consumir(ativacao, TipoTokenConta.ATIVACAO).getId()).isEqualTo(pessoa.getId());
    }

    @Test
    void invalidarApagaTodosOsPendentes() {
        String ativacao = service.emitir(pessoa, TipoTokenConta.ATIVACAO);
        String recuperacao = service.emitir(pessoa, TipoTokenConta.RECUPERACAO);

        service.invalidar(pessoa);

        assertThatThrownBy(() -> service.consumir(ativacao, TipoTokenConta.ATIVACAO))
                .isInstanceOf(TokenContaInvalidoException.class);
        assertThatThrownBy(() -> service.consumir(recuperacao, TipoTokenConta.RECUPERACAO))
                .isInstanceOf(TokenContaInvalidoException.class);
    }

    @Test
    void duasChamadasSimultaneasConsomemUmaVezSo() throws Exception {
        String token = service.emitir(pessoa, TipoTokenConta.RECUPERACAO);
        CountDownLatch largada = new CountDownLatch(1);
        Callable<Boolean> tentativa = () -> {
            largada.await();
            try {
                service.consumir(token, TipoTokenConta.RECUPERACAO);
                return true;
            } catch (TokenContaInvalidoException e) {
                return false;
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> a = pool.submit(tentativa);
            Future<Boolean> b = pool.submit(tentativa);
            largada.countDown();
            assertThat(java.util.List.of(a.get(), b.get())).containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void limpezaApagaUsadosEVencidosHaMaisDeTrintaDiasEPreservaOResto() {
        String velhoUsado = service.emitir(pessoa, TipoTokenConta.ATIVACAO);
        service.consumir(velhoUsado, TipoTokenConta.ATIVACAO);
        String velhoVencido = service.emitir(pessoa, TipoTokenConta.RECUPERACAO);
        String hashUsado = tokenConvite.hash(velhoUsado);
        String hashVencido = tokenConvite.hash(velhoVencido);

        avancar(31, ChronoUnit.DAYS);
        String recente = service.emitir(pessoa, TipoTokenConta.PROTECAO);
        new LimpezaTokensContaJob(repository, transacoes, RELOGIO).limpar();

        assertThat(repository.buscarPorHash(hashUsado)).isEmpty();
        assertThat(repository.buscarPorHash(hashVencido)).isEmpty();
        assertThat(repository.buscarPorHash(tokenConvite.hash(recente))).isPresent();
    }
}
