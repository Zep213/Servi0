package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracao;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.AlteracaoPendenteRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.escalacao.TokenConvite;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static br.com.zep.servio.pastoral.CenarioEscalacao.domingo;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Painel do coordenador e /api/me (Etapa 6, Parte 6). Datas fixas em março de 2031, para o mês do
 * painel não depender do dia em que os testes rodam.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class PainelIT {

    private static final String MES = "2031-03";

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
    AlteracaoPendenteRepository alteracaoPendenteRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    TokenConvite tokenConvite;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    CenarioEscalacao escala;
    Comunidade comunidade;
    Celebracao celebracaoA;
    Celebracao celebracaoB;
    Celebracao celebracaoC;
    Vaga vaga1A;
    Vaga vaga2B;
    Vaga vaga1C;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        escala = new CenarioEscalacao(comunidadeRepository, celebracaoRepository, vagaRepository, alocacaoRepository);
        comunidade = escala.comunidade(cenario.paroquiaA);
        celebracaoA = escala.celebracao(cenario.paroquiaA, comunidade, LocalDate.of(2031, 3, 2), TipoCelebracao.MISSA_DOMINICAL);
        celebracaoB = escala.celebracao(cenario.paroquiaA, comunidade, LocalDate.of(2031, 3, 9), TipoCelebracao.MISSA_DOMINICAL);
        celebracaoC = escala.celebracao(cenario.paroquiaA, comunidade, LocalDate.of(2031, 3, 16), TipoCelebracao.EVENTO);
        // A: quantidade 3 + uma vaga sem quantidade (fica "aguardando")
        vaga1A = escala.vaga(cenario.paroquiaA, celebracaoA, cenario.funcaoComunicacaoPascom, 3);
        escala.vaga(cenario.paroquiaA, celebracaoA, cenario.funcaoComunicacaoPascom, null);
        // B: sem ninguém
        vaga2B = escala.vaga(cenario.paroquiaA, celebracaoB, cenario.funcaoComunicacaoPascom, 2);
        // C: quantidade 1
        vaga1C = escala.vaga(cenario.paroquiaA, celebracaoC, cenario.funcaoComunicacaoPascom, 1);
    }

    private Alocacao alocar(Vaga vaga, br.com.zep.servio.model.Usuario usuario, StatusConvite status, LocalDateTime prazo) {
        Alocacao a = escala.alocacao(cenario.paroquiaA, vaga, usuario, status);
        a.setDataLimiteResposta(prazo);
        a.setTokenHash(tokenConvite.hash("token-" + UUID.randomUUID()));
        return alocacaoRepository.save(a);
    }

    private org.springframework.test.web.servlet.ResultActions painel(br.com.zep.servio.model.Usuario quem, Long pastoralId)
            throws Exception {
        return mvc.perform(get("/api/pastorais/{id}/painel", pastoralId).param("mes", MES)
                .with(user(cenario.principal(quem))));
    }

    @Test
    void cardsEStatusDasCelebracoesSeguemAsRegras() throws Exception {
        alocar(vaga1A, cenario.membro1Pascom, StatusConvite.ACEITA, null);
        alocar(vaga1A, cenario.membro2Pascom, StatusConvite.PENDENTE, LocalDateTime.of(2031, 2, 1, 0, 0));
        alocar(vaga1C, cenario.membro1Pascom, StatusConvite.ACEITA, null);

        // A: convite pendente não conta como completo; vaga sem quantidade deixa a celebração "aguardando"
        // B: ninguém ocupa a vaga → NAO_INICIADO
        // C: a única vaga com quantidade está confirmada → COMPLETO
        painel(cenario.coordenadorPascom, cenario.pascom.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.vagasTotais").value(6))
                .andExpect(jsonPath("$.cards.vagasOcupadas").value(3))
                .andExpect(jsonPath("$.cards.convitesPendentes").value(1))
                .andExpect(jsonPath("$.celebracoes[0].data").value("2031-03-02"))
                .andExpect(jsonPath("$.celebracoes[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$.celebracoes[0].aguardandoQuantidade").value(true))
                .andExpect(jsonPath("$.celebracoes[0].confirmadas").value(1))
                .andExpect(jsonPath("$.celebracoes[1].status").value("NAO_INICIADO"))
                .andExpect(jsonPath("$.celebracoes[2].status").value("COMPLETO"))
                .andExpect(jsonPath("$.celebracoes[2].aguardandoQuantidade").value(false));
    }

    @Test
    void pendenciasTraremConvitesQuePrazoRecusasSemSubstitutoEAlteracoes() throws Exception {
        // convite a 2h do prazo: entra em "vencendo" (janela padrão de 6h)
        alocar(vaga2B, cenario.membro1Pascom, StatusConvite.PENDENTE, LocalDateTime.now().plusHours(2));
        // recusa numa vaga de quantidade 2 que já tem o convite pendente do membro1: falta 1
        // (o convite pendente ocupa lugar; a recusa só entra no painel enquanto a vaga está abaixo da quantidade)
        alocar(vaga2B, cenario.secretario1Pascom, StatusConvite.RECUSADA, null);
        // alteração feita pelo vice, aguardando confirmação
        Alocacao ocupante = alocar(vaga1C, cenario.membro2Pascom, StatusConvite.ACEITA, null);
        AlteracaoPendente alteracao = new AlteracaoPendente();
        alteracao.setParoquiaId(cenario.paroquiaA.getId());
        alteracao.setAlocacao(ocupante);
        alteracao.setPastoral(cenario.pascom);
        alteracao.setAutor(cenario.vicePascom);
        alteracao.setVagaAnterior(vaga1C);
        alteracao.setUsuarioAnterior(cenario.membro2Pascom);
        alteracao.setVagaNova(vaga1C);
        alteracao.setUsuarioNovo(cenario.membro1Pascom);
        alteracao.setStatus(StatusAlteracaoPendente.PENDENTE);
        alteracaoPendenteRepository.save(alteracao);

        painel(cenario.coordenadorPascom, cenario.pascom.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendencias.convitesVencendo.length()").value(1))
                .andExpect(jsonPath("$.pendencias.convitesVencendo[0].nome").value("membro1.pascom"))
                .andExpect(jsonPath("$.pendencias.recusasSemSubstituto.length()").value(1))
                .andExpect(jsonPath("$.pendencias.recusasSemSubstituto[0].faltam").value(1))
                .andExpect(jsonPath("$.pendencias.recusasSemSubstituto[0].status").value("RECUSADA"))
                .andExpect(jsonPath("$.pendencias.alteracoesAguardando.length()").value(1))
                .andExpect(jsonPath("$.pendencias.alteracoesAguardando[0].usuarioNovo").value("membro1.pascom"))
                .andExpect(jsonPath("$.cards.alteracoesPendentes").value(1));
    }

    @Test
    void padreVeOPainelDeQualquerPastoralDaSuaParoquia() throws Exception {
        painel(cenario.padreA, cenario.pascom.getId()).andExpect(status().isOk());
    }

    @Test
    void membroComumTem403NoPainelEOutraPastoralDaoUm404() throws Exception {
        painel(cenario.membro1Pascom, cenario.pascom.getId()).andExpect(status().isForbidden());
        // coordenador do Pascom não enxerga a ECC: 404, como se ela não existisse
        painel(cenario.coordenadorPascom, cenario.ecc.getId()).andExpect(status().isNotFound());
    }

    @Test
    void meTrazAsPastoraisEOsPapeis() throws Exception {
        mvc.perform(get("/api/me").with(user(cenario.principal(cenario.coordenadorPascom))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pastorais[0].id").value(cenario.pascom.getId()))
                .andExpect(jsonPath("$.pastorais[0].nome").value("Pascom"))
                .andExpect(jsonPath("$.pastorais[0].papel").value("COORDENADOR"));
    }
}
