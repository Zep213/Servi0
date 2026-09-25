package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.AlocacaoRequestDTO;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusConvite;
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
import br.com.zep.servio.service.notification.Notificador;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parte 5.4: vice troca a pessoa de uma alocação -> a mudança aplica na hora (convite volta
 * PENDENTE) e fica registrada como pendente até o coordenador confirmar ou desfazer; coordenador
 * editando a mesma coisa não gera pendência (já é quem confirmaria).
 *
 * <p>Bug achado escrevendo esta parte (fora do que a Parte 1 original cobriu, registrar na
 * Parte 6 item 2): {@code AlteracaoPendenteService.confirmar} não checava o status atual antes
 * de confirmar (ao contrário de {@code desfazer}, que já checava) — resolver a mesma pendência
 * duas vezes (confirmar depois de já confirmada/desfeita) era aceito silenciosamente em vez de
 * dar 422. Corrigido para espelhar o mesmo guard de {@code desfazer}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class AlteracaoPendenteIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper objectMapper;
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
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    Comunidade comunidade;
    Vaga vaga;
    int contadorCandidatos;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        comunidade = new Comunidade();
        comunidade.setNome("Comunidade Central");
        comunidade.setParoquiaId(cenario.paroquiaA.getId());
        comunidade = comunidadeRepository.save(comunidade);

        Celebracao celebracao = new Celebracao();
        celebracao.setComunidade(comunidade);
        celebracao.setData(LocalDate.now().plusDays(30));
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

    private String json(Object dto) {
        return objectMapper.writeValueAsString(dto);
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

    private Alocacao criarAlocacaoDireta(Usuario usuario) {
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        alocacao.setUsuario(usuario);
        alocacao.setStatus(StatusConvite.ACEITA);
        alocacao.setParoquiaId(cenario.paroquiaA.getId());
        return alocacaoRepository.save(alocacao);
    }

    @Test
    void viceTrocaPessoaAplicaNaHoraEGeraPendenciaComAnteriorENovo() throws Exception {
        Usuario antigo = candidatoFresco();
        Usuario novo = candidatoFresco();
        Alocacao alocacao = criarAlocacaoDireta(antigo);

        String corpo = json(new AlocacaoRequestDTO(vaga.getId(), novo.getId()));
        mvc.perform(put("/api/alocacoes/{id}", alocacao.getId())
                        .with(user(cenario.principal(cenario.vicePascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuarioId").value(novo.getId()))
                .andExpect(jsonPath("$.status").value("PENDENTE"));

        List<AlteracaoPendente> pendentes = alteracaoPendenteRepository.findByAlocacaoId(alocacao.getId());
        assertThat(pendentes).hasSize(1);
        AlteracaoPendente pendente = pendentes.get(0);
        assertThat(pendente.getStatus()).isEqualTo(StatusAlteracaoPendente.PENDENTE);
        assertThat(pendente.getUsuarioAnterior().getId()).isEqualTo(antigo.getId());
        assertThat(pendente.getUsuarioNovo().getId()).isEqualTo(novo.getId());
        assertThat(pendente.getVagaAnterior().getId()).isEqualTo(vaga.getId());
        assertThat(pendente.getVagaNova().getId()).isEqualTo(vaga.getId());
        assertThat(pendente.getAutor().getId()).isEqualTo(cenario.vicePascom.getId());
        assertThat(pendente.getPastoral().getId()).isEqualTo(cenario.pascom.getId());
    }

    @Test
    void coordenadorEditandoNaoGeraPendencia() throws Exception {
        Usuario antigo = candidatoFresco();
        Usuario novo = candidatoFresco();
        Alocacao alocacao = criarAlocacaoDireta(antigo);

        String corpo = json(new AlocacaoRequestDTO(vaga.getId(), novo.getId()));
        mvc.perform(put("/api/alocacoes/{id}", alocacao.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        assertThat(alteracaoPendenteRepository.findByAlocacaoId(alocacao.getId())).isEmpty();
    }

    private AlteracaoPendente criarPendenteViaVice() throws Exception {
        Usuario antigo = candidatoFresco();
        Usuario novo = candidatoFresco();
        Alocacao alocacao = criarAlocacaoDireta(antigo);
        String corpo = json(new AlocacaoRequestDTO(vaga.getId(), novo.getId()));
        mvc.perform(put("/api/alocacoes/{id}", alocacao.getId())
                        .with(user(cenario.principal(cenario.vicePascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        return alteracaoPendenteRepository.findByAlocacaoId(alocacao.getId()).get(0);
    }

    @Test
    void coordenadorConfirmaPendencia() throws Exception {
        AlteracaoPendente pendente = criarPendenteViaVice();
        mvc.perform(post("/api/alteracoes-pendentes/{id}/confirmar", pendente.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA"));
    }

    @Test
    void coordenadorDesfazPendenciaEAlocacaoVoltaAoAnterior() throws Exception {
        Usuario antigo = candidatoFresco();
        Usuario novo = candidatoFresco();
        Alocacao alocacao = criarAlocacaoDireta(antigo);
        String corpo = json(new AlocacaoRequestDTO(vaga.getId(), novo.getId()));
        mvc.perform(put("/api/alocacoes/{id}", alocacao.getId())
                        .with(user(cenario.principal(cenario.vicePascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());
        AlteracaoPendente pendente = alteracaoPendenteRepository.findByAlocacaoId(alocacao.getId()).get(0);

        mvc.perform(post("/api/alteracoes-pendentes/{id}/desfazer", pendente.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESFEITA"));

        Alocacao recarregada = alocacaoRepository.findById(alocacao.getId()).orElseThrow();
        assertThat(recarregada.getUsuario().getId()).isEqualTo(antigo.getId());
        assertThat(recarregada.getStatus()).isEqualTo(StatusConvite.PENDENTE);
        assertThat(recarregada.getDataLimiteResposta()).isNotNull();
    }

    @Test
    void resolverPendenciaDuasVezesDaErro() throws Exception {
        AlteracaoPendente pendente = criarPendenteViaVice();
        mvc.perform(post("/api/alteracoes-pendentes/{id}/confirmar", pendente.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/alteracoes-pendentes/{id}/confirmar", pendente.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/alteracoes-pendentes/{id}/desfazer", pendente.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void viceConfirmandoDaForbidden() throws Exception {
        AlteracaoPendente pendente = criarPendenteViaVice();
        mvc.perform(post("/api/alteracoes-pendentes/{id}/confirmar", pendente.getId())
                        .with(user(cenario.principal(cenario.vicePascom))).with(csrf()))
                .andExpect(status().isForbidden());
    }
}
