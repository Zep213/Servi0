package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.notification.Notificador;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static br.com.zep.servio.pastoral.CenarioEscalacao.domingo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sorteio da Etapa 6 de ponta a ponta. Sem {@code @Transactional} na classe: o teste de
 * concorrência precisa de commits de verdade, e as duas threads têm que enxergar o mesmo banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SorteioIT {

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
    AlocacaoRepository alocacaoRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    List<Usuario> membrosExtras;
    Comunidade comunidade;
    Funcao funcaoLeitura;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        comunidade = escala.comunidade(cenario.paroquiaA);
        // Só MEMBRO é sorteado: com 5 a mais, a Pascom tem 7 sorteáveis (fora a coordenação).
        membrosExtras = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            membrosExtras.add(cenario.naPascom("membro.extra" + i + ".pascom", PapelPastoral.MEMBRO));
        }

        funcaoLeitura = new Funcao();
        funcaoLeitura.setNome("Leitura");
        funcaoLeitura.setPastoral(cenario.pascom);
        funcaoLeitura.setParoquiaId(cenario.paroquiaA.getId());
        funcaoLeitura = funcaoRepository.save(funcaoLeitura);
    }

    /** Pascom: 7 sorteáveis (papel MEMBRO). ECC: 3 membros, que não podem ser tocados pelo sorteio da Pascom. */
    private List<Usuario> membrosPascom() {
        List<Usuario> membros = new java.util.ArrayList<>(List.of(cenario.membro1Pascom, cenario.membro2Pascom));
        membros.addAll(membrosExtras);
        return membros;
    }

    private Celebracao missa() {
        return escala.celebracao(cenario.paroquiaA, comunidade, domingo(5), TipoCelebracao.MISSA_DOMINICAL);
    }

    private Vaga vagaComunicacao(Celebracao celebracao, Integer quantidade) {
        return escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoComunicacaoPascom, quantidade);
    }

    private ResultActions sortearCelebracao(Usuario quem, Celebracao celebracao) throws Exception {
        return mvc.perform(post("/api/pastorais/{p}/celebracoes/{c}/sortear",
                        cenario.pascom.getId(), celebracao.getId())
                .with(user(cenario.principal(quem))).with(csrf()));
    }

    private Set<Long> usuariosAtivos(Vaga vaga) {
        return alocacaoRepository.findByVagaIdAndActiveTrue(vaga.getId()).stream()
                .filter(a -> StatusConvite.OCUPANTES.contains(a.getStatus()))
                .map(a -> a.getUsuario().getId())
                .collect(Collectors.toSet());
    }

    private Set<Long> ids(List<Usuario> usuarios) {
        return usuarios.stream().map(Usuario::getId).collect(Collectors.toSet());
    }

    @Test
    void sorteiaSoAsVagasDaPastoralENaoTocaAOutra() throws Exception {
        Celebracao celebracao = missa();
        Vaga pascom = vagaComunicacao(celebracao, 3);
        Vaga ecc = escala.vaga(cenario.paroquiaA, celebracao, cenario.funcaoBarracaEcc, 2);

        sortearCelebracao(cenario.vicePascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(3))
                .andExpect(jsonPath("$.vagasIncompletas.length()").value(0));

        assertThat(usuariosAtivos(pascom)).hasSize(3).isSubsetOf(ids(membrosPascom()));
        assertThat(usuariosAtivos(ecc)).isEmpty();
    }

    @Test
    void vagaSemQuantidadeFicaDeFora() throws Exception {
        Celebracao celebracao = missa();
        Vaga comQuantidade = vagaComunicacao(celebracao, 2);
        Vaga semQuantidade = escala.vaga(cenario.paroquiaA, celebracao, funcaoLeitura, null);

        sortearCelebracao(cenario.coordenadorPascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(2));

        assertThat(usuariosAtivos(comQuantidade)).hasSize(2);
        assertThat(usuariosAtivos(semQuantidade)).isEmpty();
    }

    @Test
    void eventoNaoPodeSerSorteadoEDaErro422() throws Exception {
        Celebracao festa = escala.celebracao(cenario.paroquiaA, comunidade, domingo(6), TipoCelebracao.EVENTO);
        vagaComunicacao(festa, 2);

        sortearCelebracao(cenario.coordenadorPascom, festa)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Festividades são escaladas pelo coordenador"));
    }

    @Test
    void poucosElegiveisPreencheOQueDaEDevolveAVagaIncompleta() throws Exception {
        Celebracao celebracao = missa();
        Vaga vaga = vagaComunicacao(celebracao, 10);

        sortearCelebracao(cenario.coordenadorPascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(7))
                .andExpect(jsonPath("$.vagasIncompletas.length()").value(1))
                .andExpect(jsonPath("$.vagasIncompletas[0].vagaId").value(vaga.getId()))
                .andExpect(jsonPath("$.vagasIncompletas[0].faltam").value(3))
                .andExpect(jsonPath("$.vagasIncompletas[0].funcao.nome").value("Comunicação"));

        assertThat(usuariosAtivos(vaga)).hasSize(7);
    }

    @Test
    void naoSorteiaQuemServiuNoDomingoAnterior() throws Exception {
        Celebracao anterior = escala.celebracao(cenario.paroquiaA, comunidade, domingo(4), TipoCelebracao.MISSA_DOMINICAL);
        Vaga vagaAnterior = escala.vaga(cenario.paroquiaA, anterior, cenario.funcaoComunicacaoPascom, 1);
        escala.alocacao(cenario.paroquiaA, vagaAnterior, cenario.membro1Pascom, StatusConvite.ACEITA);

        Celebracao celebracao = escala.celebracao(cenario.paroquiaA, comunidade,
                anterior.getData().plusDays(7), TipoCelebracao.MISSA_DOMINICAL);
        Vaga vaga = vagaComunicacao(celebracao, 10);

        sortearCelebracao(cenario.coordenadorPascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(6));

        assertThat(usuariosAtivos(vaga)).doesNotContain(cenario.membro1Pascom.getId());
    }

    @Test
    void quemRecusouAVagaNaoEEscolhidoDeNovoNoSorteio() throws Exception {
        Celebracao celebracao = missa();
        Vaga vaga = vagaComunicacao(celebracao, 7);
        escala.alocacao(cenario.paroquiaA, vaga, cenario.membro2Pascom, StatusConvite.RECUSADA);

        sortearCelebracao(cenario.coordenadorPascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(6))
                .andExpect(jsonPath("$.vagasIncompletas[0].faltam").value(1));

        assertThat(usuariosAtivos(vaga)).doesNotContain(cenario.membro2Pascom.getId());
    }

    @Test
    void sortearDeNovoNaoDuplica() throws Exception {
        Celebracao celebracao = missa();
        Vaga vaga = vagaComunicacao(celebracao, 3);

        sortearCelebracao(cenario.coordenadorPascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(3));
        sortearCelebracao(cenario.coordenadorPascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(0));

        assertThat(alocacaoRepository.findByVagaIdAndActiveTrue(vaga.getId())).hasSize(3);
    }

    @Test
    void duasThreadsSorteandoAMesmaVagaNuncaPassamDaQuantidade() throws Exception {
        Celebracao celebracao = missa();
        Vaga vaga = vagaComunicacao(celebracao, 3);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier barreira = new CyclicBarrier(2);
            Callable<Integer> sorteio = () -> {
                barreira.await();
                return sortearVaga(cenario.coordenadorPascom, vaga).andReturn().getResponse().getStatus();
            };
            Future<Integer> primeiro = executor.submit(sorteio);
            Future<Integer> segundo = executor.submit(sorteio);

            assertThat(primeiro.get()).isEqualTo(200);
            assertThat(segundo.get()).isEqualTo(200);
        } finally {
            executor.shutdown();
        }

        assertThat(usuariosAtivos(vaga)).hasSize(3);
        assertThat(alocacaoRepository.findByVagaIdAndActiveTrue(vaga.getId())).hasSize(3);
    }

    private ResultActions sortearVaga(Usuario quem, Vaga vaga) throws Exception {
        return mvc.perform(post("/api/vagas/{id}/sortear", vaga.getId())
                .with(user(cenario.principal(quem))).with(csrf()));
    }

    @Test
    void secretarioTesoureiroEMembroRecebem403() throws Exception {
        Celebracao celebracao = missa();
        vagaComunicacao(celebracao, 2);

        for (Usuario quem : List.of(cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            sortearCelebracao(quem, celebracao).andExpect(status().isForbidden());
        }
    }

    @Test
    void pastoralDeOutraParoquiaOuSemVisibilidadeRecebe404() throws Exception {
        Celebracao celebracao = missa();
        Vaga vaga = vagaComunicacao(celebracao, 2);

        sortearCelebracao(cenario.coordenadorEcc, celebracao).andExpect(status().isNotFound());
        sortearVaga(cenario.coordenadorEcc, vaga).andExpect(status().isNotFound());
        assertThat(usuariosAtivos(vaga)).isEmpty();
    }

    @Test
    void soMembroESorteadoCoordenacaoECargosFicamDeFora() throws Exception {
        Usuario tecnico = cenario.naPascom("tecnico.pascom", PapelPastoral.TECNICO);
        Usuario redes = cenario.naPascom("redes.pascom", PapelPastoral.REDES_SOCIAIS);
        Celebracao celebracao = missa();
        Vaga vaga = vagaComunicacao(celebracao, 20);

        sortearCelebracao(cenario.coordenadorPascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(7))
                .andExpect(jsonPath("$.vagasIncompletas[0].faltam").value(13));

        assertThat(usuariosAtivos(vaga)).isEqualTo(ids(membrosPascom()))
                .doesNotContainAnyElementsOf(ids(List.of(cenario.coordenadorPascom, cenario.vicePascom,
                        cenario.secretario1Pascom, cenario.secretario2Pascom, cenario.tesoureiroPascom, tecnico, redes)));
    }

    @Test
    void tecnicoSorteiaComoOVice() throws Exception {
        Usuario tecnico = cenario.naPascom("tecnico.pascom", PapelPastoral.TECNICO);
        Celebracao celebracao = missa();
        vagaComunicacao(celebracao, 2);

        sortearCelebracao(tecnico, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(2));
    }

    @Test
    void redesSociaisNaoSorteia() throws Exception {
        Usuario redes = cenario.naPascom("redes.pascom", PapelPastoral.REDES_SOCIAIS);
        Celebracao celebracao = missa();
        vagaComunicacao(celebracao, 2);

        sortearCelebracao(redes, celebracao).andExpect(status().isForbidden());
    }

    @Test
    void viceSorteia() throws Exception {
        Celebracao celebracao = missa();
        vagaComunicacao(celebracao, 2);

        sortearCelebracao(cenario.vicePascom, celebracao)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convidados.length()").value(2));
    }
}
