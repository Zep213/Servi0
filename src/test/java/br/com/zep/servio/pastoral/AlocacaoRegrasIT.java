package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.AlocacaoRequestDTO;
import br.com.zep.servio.model.dto.PastoralConfigRequestDTO;
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
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Parte 5.6: regras de alocação — duplicidade, edição sem mudar a escalação, quem recusou pode
 * ser convidado de novo, intervalo mínimo (bloqueio, liberação ao mudar o parâmetro, e que
 * desligar numa pastoral não vaza pra outra), e vaga sem quantidade não aceita alocação.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class AlocacaoRegrasIT {

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
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    Comunidade comunidade;
    int contadorCandidatos;

    @BeforeEach
    void setUp() {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        comunidade = new Comunidade();
        comunidade.setNome("Comunidade Central");
        comunidade.setParoquiaId(cenario.paroquiaA.getId());
        comunidade = comunidadeRepository.save(comunidade);
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

    private Celebracao criarCelebracaoDireta(LocalDate data, LocalTime hora) {
        Celebracao celebracao = new Celebracao();
        celebracao.setComunidade(comunidade);
        celebracao.setData(data);
        celebracao.setHora(hora);
        celebracao.setParoquiaId(cenario.paroquiaA.getId());
        return celebracaoRepository.save(celebracao);
    }

    private Vaga criarVagaDireta(Celebracao celebracao, Funcao funcao, Integer quantidade) {
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        vaga.setFuncao(funcao);
        vaga.setQuantidade(quantidade);
        vaga.setParoquiaId(cenario.paroquiaA.getId());
        return vagaRepository.save(vaga);
    }

    private Alocacao criarAlocacaoDireta(Vaga vaga, Usuario usuario, StatusConvite status) {
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        alocacao.setUsuario(usuario);
        alocacao.setStatus(status);
        alocacao.setParoquiaId(cenario.paroquiaA.getId());
        return alocacaoRepository.save(alocacao);
    }

    @Test
    void criarAlocacaoDuplicadaDaConflito() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(40), LocalTime.of(10, 0));
        Vaga vaga = criarVagaDireta(celebracao, cenario.funcaoComunicacaoPascom, 5);
        Usuario candidato = candidatoFresco();
        criarAlocacaoDireta(vaga, candidato, StatusConvite.ACEITA);

        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidato.getId()))))
                .andExpect(status().isConflict());
    }

    @Test
    void editarAlocacaoAceitaSemMudarPessoaMantemStatusAceita() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(41), LocalTime.of(10, 0));
        Vaga vaga = criarVagaDireta(celebracao, cenario.funcaoComunicacaoPascom, 5);
        Usuario candidato = candidatoFresco();
        Alocacao alocacao = criarAlocacaoDireta(vaga, candidato, StatusConvite.ACEITA);

        mvc.perform(put("/api/alocacoes/{id}", alocacao.getId()).with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidato.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACEITA"));
    }

    @Test
    void usuarioQueRecusouPodeSerConvidadoDeNovo() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(42), LocalTime.of(10, 0));
        Vaga vaga = criarVagaDireta(celebracao, cenario.funcaoComunicacaoPascom, 5);
        Usuario candidato = candidatoFresco();
        criarAlocacaoDireta(vaga, candidato, StatusConvite.RECUSADA);

        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidato.getId()))))
                .andExpect(status().isCreated());
    }

    @Test
    void intervaloMinimoBloqueiaComMotivo() throws Exception {
        Celebracao celebracao1 = criarCelebracaoDireta(LocalDate.now().plusDays(43), LocalTime.of(10, 0));
        Vaga vaga1 = criarVagaDireta(celebracao1, cenario.funcaoComunicacaoPascom, 5);
        Usuario candidato = candidatoFresco();
        criarAlocacaoDireta(vaga1, candidato, StatusConvite.ACEITA);

        Celebracao celebracao2 = criarCelebracaoDireta(LocalDate.now().plusDays(43), LocalTime.of(12, 0));
        Vaga vaga2 = criarVagaDireta(celebracao2, cenario.funcaoComunicacaoPascom, 5);

        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga2.getId(), candidato.getId()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Intervalo mínimo")));
    }

    @Test
    void mudarIntervaloMinimoLiberaNaHora() throws Exception {
        Celebracao celebracao1 = criarCelebracaoDireta(LocalDate.now().plusDays(44), LocalTime.of(10, 0));
        Vaga vaga1 = criarVagaDireta(celebracao1, cenario.funcaoComunicacaoPascom, 5);
        Usuario candidato = candidatoFresco();
        criarAlocacaoDireta(vaga1, candidato, StatusConvite.ACEITA);

        Celebracao celebracao2 = criarCelebracaoDireta(LocalDate.now().plusDays(44), LocalTime.of(12, 0));
        Vaga vaga2 = criarVagaDireta(celebracao2, cenario.funcaoComunicacaoPascom, 5);

        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga2.getId(), candidato.getId()))))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/pastorais/{id}/config/INTERVALO_MINIMO", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PastoralConfigRequestDTO(true, Map.of("horas", 1)))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga2.getId(), candidato.getId()))))
                .andExpect(status().isCreated());
    }

    @Test
    void desligarRegraNumaPastoralNaoAfetaOutra() throws Exception {
        mvc.perform(put("/api/pastorais/{id}/config/INTERVALO_MINIMO", cenario.pascom.getId())
                        .with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PastoralConfigRequestDTO(false, Map.of("horas", 48)))))
                .andExpect(status().isOk());

        Celebracao celebracao1 = criarCelebracaoDireta(LocalDate.now().plusDays(45), LocalTime.of(10, 0));
        Vaga vagaPascom1 = criarVagaDireta(celebracao1, cenario.funcaoComunicacaoPascom, 5);
        Usuario candidato = candidatoFresco();
        criarAlocacaoDireta(vagaPascom1, candidato, StatusConvite.ACEITA);

        // Pascom com a regra desligada: alocação próxima no tempo passa direto.
        Celebracao celebracao2 = criarCelebracaoDireta(LocalDate.now().plusDays(45), LocalTime.of(12, 0));
        Vaga vagaPascom2 = criarVagaDireta(celebracao2, cenario.funcaoComunicacaoPascom, 5);
        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vagaPascom2.getId(), candidato.getId()))))
                .andExpect(status().isCreated());

        // ECC continua com a regra padrão (48h) ativa: mesma proximidade bloqueia.
        Celebracao celebracao3 = criarCelebracaoDireta(LocalDate.now().plusDays(45), LocalTime.of(16, 0));
        Vaga vagaEcc = criarVagaDireta(celebracao3, cenario.funcaoBarracaEcc, 5);
        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorEcc))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vagaEcc.getId(), candidato.getId()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void vagaSemQuantidadeNaoAceitaAlocacao() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(46), LocalTime.of(10, 0));
        Vaga vaga = criarVagaDireta(celebracao, cenario.funcaoComunicacaoPascom, null);
        Usuario candidato = candidatoFresco();

        mvc.perform(post("/api/alocacoes").with(user(cenario.principal(cenario.coordenadorPascom))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidato.getId()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("quantidade")));
    }
}
