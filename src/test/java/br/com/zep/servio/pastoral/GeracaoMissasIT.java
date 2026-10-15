package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.HorarioMissa;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.HorarioMissaRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.agenda.GeracaoMissasJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Geração automática de missas (Sprint 4, cartão 2). Relógio fixo em segunda, 14/12/2026 às 12:00 (o teste avança
 * quando precisa) e job ligado só nesta classe. Sem {@code @Transactional}: a geração precisa comitar de verdade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "servio.jobs.enabled=true")
class GeracaoMissasIT {

    private static final ZoneId FUSO = ZoneId.of("America/Fortaleza");
    static final RelogioDeTeste RELOGIO = new RelogioDeTeste();

    @TestConfiguration
    static class ConfigRelogio {
        @Bean
        @Primary
        Clock relogioDeTeste() {
            return RELOGIO;
        }
    }

    static final class RelogioDeTeste extends Clock {
        private volatile Instant instante = LocalDateTime.of(2026, 12, 14, 12, 0).atZone(FUSO).toInstant();

        void definir(LocalDateTime agora) {
            instante = agora.atZone(FUSO).toInstant();
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
    MockMvc mvc;
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
    ComunidadeRepository comunidadeRepository;
    @Autowired
    CelebracaoRepository celebracaoRepository;
    @Autowired
    VagaRepository vagaRepository;
    @Autowired
    HorarioMissaRepository horarioMissaRepository;
    @Autowired
    GeracaoMissasJob job;
    @Autowired
    PasswordEncoder passwordEncoder;

    CenarioParoquia cenario;
    Comunidade matriz;

    @BeforeEach
    void setUp() {
        RELOGIO.definir(LocalDateTime.of(2026, 12, 14, 12, 0));
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        matriz = comunidade(cenario.paroquiaA.getId(), "Matriz");
    }

    private Comunidade comunidade(Long paroquiaId, String nome) {
        Comunidade comunidade = new Comunidade();
        comunidade.setNome(nome);
        comunidade.setParoquiaId(paroquiaId);
        return comunidadeRepository.save(comunidade);
    }

    private HorarioMissa horario(Comunidade comunidade, String hora) {
        HorarioMissa horario = new HorarioMissa();
        horario.setComunidade(comunidade);
        horario.setDiaSemana((short) DayOfWeek.SUNDAY.getValue());
        horario.setHora(LocalTime.parse(hora));
        horario.setParoquiaId(comunidade.getParoquiaId());
        return horarioMissaRepository.save(horario);
    }

    private List<Celebracao> celebracoesDe(HorarioMissa horario) {
        return celebracaoRepository.findAll().stream()
                .filter(c -> c.getHorarioMissa() != null && c.getHorarioMissa().getId().equals(horario.getId()))
                .sorted((a, b) -> a.getData().compareTo(b.getData()))
                .toList();
    }

    private ResultActions como(Usuario quem, MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao.with(user(cenario.principal(quem))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void geraOitoDomingosEAsMissasDeJaneiroAparecemSozinhas() {
        HorarioMissa dez = horario(matriz, "10:00");

        job.executar();

        List<Celebracao> geradas = celebracoesDe(dez);
        assertThat(geradas).hasSize(8);
        assertThat(geradas.get(0).getData()).isEqualTo(LocalDate.of(2026, 12, 20));
        assertThat(geradas.get(7).getData()).isEqualTo(LocalDate.of(2027, 2, 7));
        assertThat(geradas).allSatisfy(c -> {
            assertThat(c.getTipo()).isEqualTo(TipoCelebracao.MISSA_DOMINICAL);
            assertThat(c.getHora()).isEqualTo(LocalTime.of(10, 0));
            assertThat(c.getData().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
            assertThat(c.isActive()).isTrue();
        });
        assertThat(geradas).extracting(Celebracao::getData)
                .contains(LocalDate.of(2027, 1, 3), LocalDate.of(2027, 1, 10), LocalDate.of(2027, 1, 31));
    }

    @Test
    void rodarDeNovoNaoCriaNadaEUmaSemanaDepoisSoApareceASemanaNova() {
        HorarioMissa dez = horario(matriz, "10:00");
        job.executar();

        job.executar();
        assertThat(celebracoesDe(dez)).hasSize(8);

        RELOGIO.definir(LocalDateTime.of(2026, 12, 21, 12, 0));
        job.executar();
        List<Celebracao> depois = celebracoesDe(dez);
        assertThat(depois).hasSize(9);
        assertThat(depois.get(8).getData()).isEqualTo(LocalDate.of(2027, 2, 14));
    }

    @Test
    void missaCanceladaPeloPadreNaoVolta() {
        HorarioMissa dez = horario(matriz, "10:00");
        job.executar();
        Celebracao cancelada = celebracoesDe(dez).stream()
                .filter(c -> c.getData().equals(LocalDate.of(2027, 1, 3))).findFirst().orElseThrow();
        cancelada.setActive(false);
        celebracaoRepository.save(cancelada);

        job.executar();

        List<Celebracao> todas = celebracoesDe(dez);
        assertThat(todas).hasSize(8);
        assertThat(todas).filteredOn(c -> c.getData().equals(LocalDate.of(2027, 1, 3)))
                .singleElement().satisfies(c -> assertThat(c.isActive()).isFalse());
    }

    @Test
    void duasExecucoesAoMesmoTempoNaoDuplicamData() throws Exception {
        HorarioMissa dez = horario(matriz, "10:00");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<?>> execucoes = List.of(
                    pool.submit(() -> { largada.await(); job.executar(); return null; }),
                    pool.submit(() -> { largada.await(); job.executar(); return null; }));
            largada.countDown();
            for (Future<?> execucao : execucoes) {
                execucao.get();
            }
        } finally {
            pool.shutdown();
        }
        // o índice único decide: sobra uma linha por data, mesmo que uma das execuções tenha esbarrado nele
        job.executar();
        assertThat(celebracoesDe(dez)).extracting(Celebracao::getData).doesNotHaveDuplicates().hasSize(8);
    }

    @Test
    void horarioDesativadoNaoGeraEAsFuturasJaGeradasFicam() throws Exception {
        HorarioMissa dez = horario(matriz, "10:00");
        job.executar();
        como(cenario.padreA, delete("/api/comunidades/{c}/horarios-missa/{id}", matriz.getId(), dez.getId()))
                .andExpect(status().isNoContent());

        RELOGIO.definir(LocalDateTime.of(2026, 12, 21, 12, 0));
        job.executar();

        assertThat(celebracoesDe(dez)).hasSize(8).allMatch(Celebracao::isActive);
    }

    @Test
    void editarAHoraVaiSoParaAsDatasAindaNaoGeradas() throws Exception {
        HorarioMissa dez = horario(matriz, "10:00");
        job.executar();

        como(cenario.padreA, put("/api/comunidades/{c}/horarios-missa/{id}", matriz.getId(), dez.getId())
                .content("{\"hora\":\"11:00\"}")).andExpect(status().isOk());
        RELOGIO.definir(LocalDateTime.of(2026, 12, 21, 12, 0));
        job.executar();

        List<Celebracao> todas = celebracoesDe(dez);
        assertThat(todas).hasSize(9);
        assertThat(todas.subList(0, 8)).allMatch(c -> c.getHora().equals(LocalTime.of(10, 0)));
        assertThat(todas.get(8).getHora()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void criarOHorarioPelaApiJaGeraAsMissasSemEsperarOJob() throws Exception {
        String resposta = como(cenario.padreA, post("/api/comunidades/{id}/horarios-missa", matriz.getId())
                .content("{\"hora\":\"09:00\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = com.jayway.jsonpath.JsonPath.parse(resposta).read("$.id", Long.class);

        assertThat(celebracoesDe(horarioMissaRepository.findById(id).orElseThrow())).hasSize(8);
    }

    @Test
    void soAPastoralQueCobreOHorarioGanhaAsVagasDasMissasGeradas() throws Exception {
        Funcao funcaoPascom = funcao(cenario.pascom, "Fotografia");
        Funcao funcaoEcc = funcao(cenario.ecc, "Recepção");
        ligarCoberturaComModelo(cenario.pascom, funcaoPascom);
        ligarCoberturaComModelo(cenario.ecc, funcaoEcc);
        HorarioMissa dez = horario(matriz, "10:00");
        como(cenario.coordenadorPascom, put("/api/pastorais/{id}/cobertura-missa", cenario.pascom.getId())
                .content("{\"horarioMissaIds\":[" + dez.getId() + "]}")).andExpect(status().isOk());
        // ECC não escolheu horário nenhum: não recebe vaga nas missas geradas

        job.executar();

        for (Celebracao celebracao : celebracoesDe(dez)) {
            assertThat(vagaRepository.findByCelebracaoIdAndActiveTrue(celebracao.getId()))
                    .singleElement()
                    // só o id: getFuncao() é proxy lazy e o teste roda fora de sessão
                    .satisfies(v -> assertThat(v.getFuncao().getId()).isEqualTo(funcaoPascom.getId()));
        }
    }

    @Test
    void cadaParoquiaSoRecebeAsSuas() {
        Comunidade deB = comunidade(cenario.paroquiaB.getId(), "Comunidade de B");
        HorarioMissa horarioA = horario(matriz, "10:00");
        HorarioMissa horarioB = horario(deB, "08:00");

        job.executar();

        assertThat(celebracoesDe(horarioA)).hasSize(8)
                .allMatch(c -> c.getParoquiaId().equals(cenario.paroquiaA.getId()));
        assertThat(celebracoesDe(horarioB)).hasSize(8)
                .allMatch(c -> c.getParoquiaId().equals(cenario.paroquiaB.getId()));
    }

    private Funcao funcao(Pastoral pastoral, String nome) {
        Funcao funcao = new Funcao();
        funcao.setNome(nome);
        funcao.setPastoral(pastoral);
        funcao.setParoquiaId(cenario.paroquiaA.getId());
        return funcaoRepository.save(funcao);
    }

    private void ligarCoberturaComModelo(Pastoral pastoral, Funcao funcao) throws Exception {
        como(cenario.padreA, put("/api/pastorais/{id}/config/COBERTURA_AUTOMATICA", pastoral.getId())
                .content("{\"ativa\":true,\"parametros\":{}}")).andExpect(status().isOk());
        como(cenario.padreA, post("/api/pastorais/{id}/modelos-vaga", pastoral.getId())
                .content("{\"funcaoId\":" + funcao.getId() + ",\"tipoCelebracao\":\"MISSA_DOMINICAL\",\"quantidade\":2}"))
                .andExpect(status().isCreated());
    }
}
