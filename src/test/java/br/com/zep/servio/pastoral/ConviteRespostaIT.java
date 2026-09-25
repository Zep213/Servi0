package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.model.enumerated.StatusConvite;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parte 5.5: aceitar/recusar convite, responder convite de outra pessoa, responder duas vezes,
 * e prazo vencido. Deliberadamente SEM {@code @Transactional} na classe (pedido explícito da
 * Parte 5.5): o caso de prazo vencido exercita o {@code noRollbackFor} da Parte 1.5
 * ({@code AlocacaoService.responder}), que precisa realmente COMMITAR o status EXPIRADA no banco
 * mesmo lançando exceção — envolver o teste numa transação que sempre reverte no fim mascararia
 * esse comportamento (o commit intermediário só é distinguível de um rollback completo se a
 * verificação relê do banco fora de qualquer transação que ainda vá reverter).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class ConviteRespostaIT {

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
    Vaga vaga;
    int contadorCandidatos;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        Comunidade comunidade = new Comunidade();
        comunidade.setNome("Comunidade Central " + System.nanoTime());
        comunidade.setParoquiaId(cenario.paroquiaA.getId());
        comunidade = comunidadeRepository.save(comunidade);

        Celebracao celebracao = new Celebracao();
        celebracao.setComunidade(comunidade);
        celebracao.setData(LocalDate.now().plusDays(35));
        celebracao.setHora(LocalTime.of(10, 0));
        celebracao.setParoquiaId(cenario.paroquiaA.getId());
        celebracao = celebracaoRepository.save(celebracao);

        vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        vaga.setFuncao(cenario.funcaoComunicacaoPascom);
        vaga.setQuantidade(10);
        vaga.setParoquiaId(cenario.paroquiaA.getId());
        vaga = vagaRepository.save(vaga);
    }

    private Usuario candidatoFresco() {
        contadorCandidatos++;
        Usuario usuario = new Usuario();
        usuario.setNome("Candidato " + contadorCandidatos);
        usuario.setEmail("candidato" + contadorCandidatos + "." + System.nanoTime() + "@servio.dev");
        usuario.setSenha(passwordEncoder.encode(CenarioParoquia.SENHA));
        usuario.setPerfil(Perfil.SERVIDOR);
        usuario.setParoquiaId(cenario.paroquiaA.getId());
        return usuarioRepository.save(usuario);
    }

    private Alocacao criarConvitePendente(Usuario usuario, LocalDateTime dataLimiteResposta) {
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        alocacao.setUsuario(usuario);
        alocacao.setStatus(StatusConvite.PENDENTE);
        alocacao.setDataLimiteResposta(dataLimiteResposta);
        alocacao.setParoquiaId(cenario.paroquiaA.getId());
        return alocacaoRepository.save(alocacao);
    }

    @Test
    void aceitarConvitePendente() throws Exception {
        Usuario convidado = candidatoFresco();
        Alocacao alocacao = criarConvitePendente(convidado, LocalDateTime.now().plusHours(24));

        mvc.perform(post("/api/alocacoes/{id}/responder?aceitar=true", alocacao.getId())
                        .with(user(cenario.principal(convidado))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACEITA"));
    }

    @Test
    void recusarConvitePendente() throws Exception {
        Usuario convidado = candidatoFresco();
        Alocacao alocacao = criarConvitePendente(convidado, LocalDateTime.now().plusHours(24));

        mvc.perform(post("/api/alocacoes/{id}/responder?aceitar=false", alocacao.getId())
                        .with(user(cenario.principal(convidado))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADA"));
    }

    @Test
    void responderConviteDeOutraPessoaDaForbidden() throws Exception {
        Usuario convidado = candidatoFresco();
        Usuario outraPessoa = candidatoFresco();
        Alocacao alocacao = criarConvitePendente(convidado, LocalDateTime.now().plusHours(24));

        mvc.perform(post("/api/alocacoes/{id}/responder?aceitar=true", alocacao.getId())
                        .with(user(cenario.principal(outraPessoa))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void responderDuasVezesDaErro() throws Exception {
        Usuario convidado = candidatoFresco();
        Alocacao alocacao = criarConvitePendente(convidado, LocalDateTime.now().plusHours(24));

        mvc.perform(post("/api/alocacoes/{id}/responder?aceitar=true", alocacao.getId())
                        .with(user(cenario.principal(convidado))).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/alocacoes/{id}/responder?aceitar=true", alocacao.getId())
                        .with(user(cenario.principal(convidado))).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void prazoVencidoDaErroEStatusViraExpiradaNoBanco() throws Exception {
        Usuario convidado = candidatoFresco();
        Alocacao alocacao = criarConvitePendente(convidado, LocalDateTime.now().minusMinutes(1));

        mvc.perform(post("/api/alocacoes/{id}/responder?aceitar=true", alocacao.getId())
                        .with(user(cenario.principal(convidado))).with(csrf()))
                .andExpect(status().isUnprocessableEntity());

        Alocacao relida = alocacaoRepository.findById(alocacao.getId()).orElseThrow();
        assertThat(relida.getStatus()).isEqualTo(StatusConvite.EXPIRADA);
    }
}
