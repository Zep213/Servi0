package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.AlteracaoPendente;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Comunidade;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.dto.AlocacaoRequestDTO;
import br.com.zep.servio.model.dto.CelebracaoRequestDTO;
import br.com.zep.servio.model.dto.LancamentoFinanceiroRequestDTO;
import br.com.zep.servio.model.dto.ModeloVagaRequestDTO;
import br.com.zep.servio.model.dto.PastoralConfigRequestDTO;
import br.com.zep.servio.model.dto.PastoralRequestDTO;
import br.com.zep.servio.model.dto.ReuniaoRequestDTO;
import br.com.zep.servio.model.dto.SolicitacaoReuniaoRequestDTO;
import br.com.zep.servio.model.dto.UsuarioPastoralRequestDTO;
import br.com.zep.servio.model.dto.VagaRequestDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.StatusAlteracaoPendente;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.model.enumerated.TipoCelebracaoModelo;
import br.com.zep.servio.model.enumerated.TipoLancamento;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.AlteracaoPendenteRepository;
import br.com.zep.servio.repository.CelebracaoRepository;
import br.com.zep.servio.repository.ComunidadeRepository;
import br.com.zep.servio.repository.FuncaoRepository;
import br.com.zep.servio.repository.LancamentoFinanceiroRepository;
import br.com.zep.servio.repository.ParoquiaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.seguranca.ApoioTeste;
import br.com.zep.servio.seguranca.TestcontainersConfig;
import br.com.zep.servio.service.notification.Notificador;
import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz completa da Parte 5.2: cada ação na Pascom, testada com ADMIN assumido, PADRE,
 * COORDENADOR/VICE/SECRETARIO/TESOUREIRO/MEMBRO da própria Pascom, e o COORDENADOR do ECC
 * (zero relação com a Pascom, pra testar o limite entre pastorais).
 *
 * <p>Desvios da matriz original do pedido, registrados aqui e no relatório final (Parte 6,
 * item 4): a matriz pedia 403 pro COORDENADOR do ECC em "Atribuir VICE/SECR/TES/MEMBRO"; o
 * código (e este teste) usa 404, pela mesma regra de "pastoral fora do alcance" aplicada a
 * toda ação pastoral-específica nesta parte (financeiro, reuniões, config, modelo de vaga,
 * vaga, alocação, alteração pendente) — a única exceção mantida é "Solicitar reunião", cujo
 * gate é sobre a própria participação do autor, não sobre a visibilidade da pastoral alheia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@Transactional
class PermissaoPorPapelIT {

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
    LancamentoFinanceiroRepository lancamentoFinanceiroRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    Notificador notificador;

    CenarioParoquia cenario;
    Comunidade comunidade;
    Cookie sessaoAdmin;
    int contadorCandidatos;

    /**
     * O app usa Spring Session (persistida no banco): MockMvc.session(mockHttpSession) não é
     * respeitado pelo SessionRepositoryFilter, que resolve a sessão pelo cookie. Por isso o
     * ADMIN loga de verdade e reusa o cookie real em toda chamada — é o único jeito da
     * paróquia assumida (guardada na sessão HTTP, Parte 2.2) sobreviver entre requisições.
     */
    @BeforeEach
    void setUp() throws Exception {
        cenario = new CenarioParoquia(paroquiaRepository, usuarioRepository, pastoralRepository,
                funcaoRepository, usuarioPastoralRepository, passwordEncoder).criar();
        comunidade = new Comunidade();
        comunidade.setNome("Comunidade Central");
        comunidade.setParoquiaId(cenario.paroquiaA.getId());
        comunidade = comunidadeRepository.save(comunidade);
        sessaoAdmin = ApoioTeste.login(mvc, cenario.admin.getEmail(), CenarioParoquia.SENHA);
        mvc.perform(post("/api/plataforma/paroquias/{id}/assumir", cenario.paroquiaA.getId())
                        .cookie(sessaoAdmin).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private ResultActions como(Usuario usuario, MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.with(user(cenario.principal(usuario))).with(csrf()));
    }

    private ResultActions comoAdmin(MockHttpServletRequestBuilder builder) throws Exception {
        return mvc.perform(builder.cookie(sessaoAdmin).with(csrf()));
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

    private Celebracao criarCelebracaoDireta(LocalDate data) {
        Celebracao celebracao = new Celebracao();
        celebracao.setComunidade(comunidade);
        celebracao.setData(data);
        celebracao.setHora(LocalTime.of(10, 0));
        celebracao.setParoquiaId(cenario.paroquiaA.getId());
        return celebracaoRepository.save(celebracao);
    }

    private Vaga criarVagaDireta(Celebracao celebracao, Integer quantidade) {
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        vaga.setFuncao(cenario.funcaoComunicacaoPascom);
        vaga.setQuantidade(quantidade);
        vaga.setParoquiaId(cenario.paroquiaA.getId());
        return vagaRepository.save(vaga);
    }

    private Alocacao criarAlocacaoDireta(Vaga vaga, Usuario usuario) {
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        alocacao.setUsuario(usuario);
        alocacao.setStatus(StatusConvite.ACEITA);
        alocacao.setParoquiaId(cenario.paroquiaA.getId());
        return alocacaoRepository.save(alocacao);
    }

    private AlteracaoPendente criarPendenteDireta(Alocacao alocacao) {
        AlteracaoPendente pendente = new AlteracaoPendente();
        pendente.setAlocacao(alocacao);
        pendente.setPastoral(cenario.pascom);
        pendente.setAutor(cenario.vicePascom);
        pendente.setVagaAnterior(alocacao.getVaga());
        pendente.setUsuarioAnterior(alocacao.getUsuario());
        pendente.setVagaNova(alocacao.getVaga());
        pendente.setUsuarioNovo(alocacao.getUsuario());
        pendente.setStatus(StatusAlteracaoPendente.PENDENTE);
        pendente.setParoquiaId(cenario.paroquiaA.getId());
        return alteracaoPendenteRepository.save(pendente);
    }

    @Test
    void criarPastoral() throws Exception {
        String corpo = json(new PastoralRequestDTO("Nova Pastoral"));
        comoAdmin(post("/api/pastorais").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        como(cenario.padreA, post("/api/pastorais").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        for (Usuario u : List.of(cenario.coordenadorPascom, cenario.vicePascom, cenario.secretario1Pascom,
                cenario.tesoureiroPascom, cenario.membro1Pascom, cenario.coordenadorEcc)) {
            como(u, post("/api/pastorais").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void atribuirPapelCoordenador() throws Exception {
        Usuario alvoAdmin = candidatoFresco();
        Usuario alvoPadre = candidatoFresco();
        comoAdmin(post("/api/usuarios-pastorais").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioPastoralRequestDTO(alvoAdmin.getId(), cenario.pascom.getId(), PapelPastoral.COORDENADOR))))
                .andExpect(status().isCreated());
        como(cenario.padreA, post("/api/usuarios-pastorais").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioPastoralRequestDTO(alvoPadre.getId(), cenario.pascom.getId(), PapelPastoral.COORDENADOR))))
                .andExpect(status().isCreated());

        for (Usuario ator : List.of(cenario.coordenadorPascom, cenario.vicePascom, cenario.secretario1Pascom,
                cenario.tesoureiroPascom, cenario.membro1Pascom, cenario.coordenadorEcc)) {
            Usuario alvo = candidatoFresco();
            como(ator, post("/api/usuarios-pastorais").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UsuarioPastoralRequestDTO(alvo.getId(), cenario.pascom.getId(), PapelPastoral.COORDENADOR))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void atribuirOutrosPapeis() throws Exception {
        String papel = PapelPastoral.MEMBRO.name();

        Usuario alvoAdmin = candidatoFresco();
        comoAdmin(post("/api/usuarios-pastorais").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioPastoralRequestDTO(alvoAdmin.getId(), cenario.pascom.getId(), PapelPastoral.MEMBRO))))
                .andExpect(status().isCreated());

        Usuario alvoPadre = candidatoFresco();
        como(cenario.padreA, post("/api/usuarios-pastorais").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioPastoralRequestDTO(alvoPadre.getId(), cenario.pascom.getId(), PapelPastoral.MEMBRO))))
                .andExpect(status().isCreated());

        Usuario alvoCoord = candidatoFresco();
        como(cenario.coordenadorPascom, post("/api/usuarios-pastorais").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioPastoralRequestDTO(alvoCoord.getId(), cenario.pascom.getId(), PapelPastoral.MEMBRO))))
                .andExpect(status().isCreated());

        for (Usuario ator : List.of(cenario.vicePascom, cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            Usuario alvo = candidatoFresco();
            como(ator, post("/api/usuarios-pastorais").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new UsuarioPastoralRequestDTO(alvo.getId(), cenario.pascom.getId(), PapelPastoral.MEMBRO))))
                    .andExpect(status().isForbidden());
        }

        // COORDENADOR do ECC: pastoral (Pascom) fora do alcance dele -> 404, não 403 (ver nota da classe)
        Usuario alvoEcc = candidatoFresco();
        como(cenario.coordenadorEcc, post("/api/usuarios-pastorais").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new UsuarioPastoralRequestDTO(alvoEcc.getId(), cenario.pascom.getId(), PapelPastoral.MEMBRO))))
                .andExpect(status().isNotFound());
    }

    @Test
    void criarCelebracao() throws Exception {
        String corpo = json(new CelebracaoRequestDTO(
                comunidade.getId(), LocalDate.now().plusDays(10), LocalTime.of(10, 0), null, null, null));
        comoAdmin(post("/api/celebracoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        como(cenario.padreA, post("/api/celebracoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        for (Usuario u : List.of(cenario.coordenadorPascom, cenario.vicePascom, cenario.secretario1Pascom,
                cenario.tesoureiroPascom, cenario.membro1Pascom, cenario.coordenadorEcc)) {
            como(u, post("/api/celebracoes").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void responsabilizarVagaSemQuantidade() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(11));
        String corpo = json(new VagaRequestDTO(celebracao.getId(), cenario.funcaoComunicacaoPascom.getId(), null, null, null));

        comoAdmin(post("/api/vagas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        como(cenario.padreA, post("/api/vagas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        for (Usuario u : List.of(cenario.coordenadorPascom, cenario.vicePascom, cenario.secretario1Pascom,
                cenario.tesoureiroPascom, cenario.membro1Pascom, cenario.coordenadorEcc)) {
            como(u, post("/api/vagas").contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void definirQuantidadeDaVaga() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(12));

        assertirDefinirQuantidade(celebracao, this::comoAdmin, status().isOk());
        assertirDefinirQuantidade(celebracao, b -> como(cenario.padreA, b), status().isOk());
        assertirDefinirQuantidade(celebracao, b -> como(cenario.coordenadorPascom, b), status().isOk());
        assertirDefinirQuantidade(celebracao, b -> como(cenario.vicePascom, b), status().isOk());
        assertirDefinirQuantidade(celebracao, b -> como(cenario.secretario1Pascom, b), status().isOk());
        assertirDefinirQuantidade(celebracao, b -> como(cenario.tesoureiroPascom, b), status().isForbidden());
        assertirDefinirQuantidade(celebracao, b -> como(cenario.membro1Pascom, b), status().isForbidden());
        assertirDefinirQuantidade(celebracao, b -> como(cenario.coordenadorEcc, b), status().isNotFound());
    }

    private interface Executor {
        ResultActions executar(MockHttpServletRequestBuilder builder) throws Exception;
    }

    private void assertirDefinirQuantidade(Celebracao celebracao, Executor executor,
                                            org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        Vaga vaga = criarVagaDireta(celebracao, null);
        String corpo = json(new VagaRequestDTO(celebracao.getId(), cenario.funcaoComunicacaoPascom.getId(), 3, null, null));
        executor.executar(put("/api/vagas/{id}", vaga.getId()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(esperado);
    }

    @Test
    void criarAlocacao() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(13));
        Vaga vaga = criarVagaDireta(celebracao, 10);

        comoAdmin(post("/api/alocacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidatoFresco().getId()))))
                .andExpect(status().isCreated());
        como(cenario.padreA, post("/api/alocacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidatoFresco().getId()))))
                .andExpect(status().isCreated());
        como(cenario.coordenadorPascom, post("/api/alocacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidatoFresco().getId()))))
                .andExpect(status().isCreated());
        como(cenario.vicePascom, post("/api/alocacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidatoFresco().getId()))))
                .andExpect(status().isCreated());

        for (Usuario ator : List.of(cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            como(ator, post("/api/alocacoes").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new AlocacaoRequestDTO(vaga.getId(), candidatoFresco().getId()))))
                    .andExpect(status().isForbidden());
        }
        como(cenario.coordenadorEcc, post("/api/alocacoes").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new AlocacaoRequestDTO(vaga.getId(), candidatoFresco().getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void editarAlocacaoTrocaDePessoa() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(14));
        Vaga vaga = criarVagaDireta(celebracao, 20);

        editarComo(vaga, this::comoAdmin, status().isOk());
        editarComo(vaga, b -> como(cenario.padreA, b), status().isOk());
        editarComo(vaga, b -> como(cenario.coordenadorPascom, b), status().isOk());
        editarComo(vaga, b -> como(cenario.vicePascom, b), status().isOk());
        for (Usuario ator : List.of(cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            editarComo(vaga, b -> como(ator, b), status().isForbidden());
        }
        editarComo(vaga, b -> como(cenario.coordenadorEcc, b), status().isNotFound());
    }

    private void editarComo(Vaga vaga, Executor executor, org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        Alocacao alocacao = criarAlocacaoDireta(vaga, candidatoFresco());
        String corpo = json(new AlocacaoRequestDTO(vaga.getId(), candidatoFresco().getId()));
        executor.executar(put("/api/alocacoes/{id}", alocacao.getId()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(esperado);
    }

    @Test
    void confirmarPendencia() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(15));
        Vaga vaga = criarVagaDireta(celebracao, 5);

        confirmarComo(vaga, this::comoAdmin, status().isOk());
        confirmarComo(vaga, b -> como(cenario.padreA, b), status().isOk());
        confirmarComo(vaga, b -> como(cenario.coordenadorPascom, b), status().isOk());
        for (Usuario ator : List.of(cenario.vicePascom, cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            confirmarComo(vaga, b -> como(ator, b), status().isForbidden());
        }
        confirmarComo(vaga, b -> como(cenario.coordenadorEcc, b), status().isNotFound());
    }

    private void confirmarComo(Vaga vaga, Executor executor, org.springframework.test.web.servlet.ResultMatcher esperado) throws Exception {
        Alocacao alocacao = criarAlocacaoDireta(vaga, candidatoFresco());
        AlteracaoPendente pendente = criarPendenteDireta(alocacao);
        executor.executar(post("/api/alteracoes-pendentes/{id}/confirmar", pendente.getId())).andExpect(esperado);
    }

    @Test
    void configurarRegrasEModeloDeVagas() throws Exception {
        String corpoConfig = json(new PastoralConfigRequestDTO(true, Map.of("horas", 60)));
        comoAdmin(put("/api/pastorais/{id}/config/INTERVALO_MINIMO", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoConfig))
                .andExpect(status().isOk());
        como(cenario.padreA, put("/api/pastorais/{id}/config/INTERVALO_MINIMO", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoConfig))
                .andExpect(status().isOk());
        como(cenario.coordenadorPascom, put("/api/pastorais/{id}/config/INTERVALO_MINIMO", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoConfig))
                .andExpect(status().isOk());
        for (Usuario ator : List.of(cenario.vicePascom, cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            como(ator, put("/api/pastorais/{id}/config/INTERVALO_MINIMO", cenario.pascom.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(corpoConfig))
                    .andExpect(status().isForbidden());
        }
        como(cenario.coordenadorEcc, put("/api/pastorais/{id}/config/INTERVALO_MINIMO", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoConfig))
                .andExpect(status().isNotFound());

        String corpoModelo = json(new ModeloVagaRequestDTO(cenario.funcaoComunicacaoPascom.getId(), TipoCelebracaoModelo.TODOS, 2));
        comoAdmin(post("/api/pastorais/{id}/modelos-vaga", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoModelo))
                .andExpect(status().isCreated());
        como(cenario.coordenadorEcc, post("/api/pastorais/{id}/modelos-vaga", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpoModelo))
                .andExpect(status().isNotFound());
    }

    @Test
    void marcarReuniao() throws Exception {
        String corpo = json(new ReuniaoRequestDTO("Reunião mensal", LocalDateTime.now().plusDays(5), "Salão paroquial"));
        comoAdmin(post("/api/pastorais/{id}/reunioes", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        como(cenario.padreA, post("/api/pastorais/{id}/reunioes", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        como(cenario.coordenadorPascom, post("/api/pastorais/{id}/reunioes", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        for (Usuario ator : List.of(cenario.vicePascom, cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            como(ator, post("/api/pastorais/{id}/reunioes", cenario.pascom.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isForbidden());
        }
        como(cenario.coordenadorEcc, post("/api/pastorais/{id}/reunioes", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void solicitarReuniao() throws Exception {
        String corpo = json(new SolicitacaoReuniaoRequestDTO("Discutir a próxima campanha"));
        comoAdmin(post("/api/pastorais/{id}/reunioes/solicitar", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());
        como(cenario.padreA, post("/api/pastorais/{id}/reunioes/solicitar", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        como(cenario.coordenadorPascom, post("/api/pastorais/{id}/reunioes/solicitar", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnprocessableEntity());
        for (Usuario ator : List.of(cenario.vicePascom, cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            como(ator, post("/api/pastorais/{id}/reunioes/solicitar", cenario.pascom.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isNoContent());
        }
        como(cenario.coordenadorEcc, post("/api/pastorais/{id}/reunioes/solicitar", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void lancarNoFinanceiro() throws Exception {
        String corpo = json(new LancamentoFinanceiroRequestDTO(TipoLancamento.ENTRADA, BigDecimal.TEN, "Doação", LocalDate.now()));
        comoAdmin(post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        como(cenario.padreA, post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
        for (Usuario ator : List.of(cenario.coordenadorPascom, cenario.vicePascom, cenario.secretario1Pascom, cenario.membro1Pascom)) {
            como(ator, post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                            .contentType(MediaType.APPLICATION_JSON).content(corpo))
                    .andExpect(status().isForbidden());
        }
        como(cenario.tesoureiroPascom, post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated());
        como(cenario.coordenadorEcc, post("/api/pastorais/{id}/financeiro", cenario.pascom.getId())
                        .contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
    }

    @Test
    void verLancamentosESaldoDaPastoral() throws Exception {
        comoAdmin(get("/api/pastorais/{id}/financeiro", cenario.pascom.getId())).andExpect(status().isOk());
        como(cenario.padreA, get("/api/pastorais/{id}/financeiro", cenario.pascom.getId())).andExpect(status().isOk());
        como(cenario.coordenadorPascom, get("/api/pastorais/{id}/financeiro", cenario.pascom.getId())).andExpect(status().isOk());
        como(cenario.tesoureiroPascom, get("/api/pastorais/{id}/financeiro", cenario.pascom.getId())).andExpect(status().isOk());
        for (Usuario ator : List.of(cenario.vicePascom, cenario.secretario1Pascom, cenario.membro1Pascom)) {
            como(ator, get("/api/pastorais/{id}/financeiro", cenario.pascom.getId())).andExpect(status().isForbidden());
        }
        como(cenario.coordenadorEcc, get("/api/pastorais/{id}/financeiro", cenario.pascom.getId())).andExpect(status().isNotFound());
    }

    @Test
    void dashboardFinanceiroConsolidado() throws Exception {
        String de = LocalDate.now().minusDays(30).toString();
        String ate = LocalDate.now().toString();
        comoAdmin(get("/api/financeiro/resumo?de={de}&ate={ate}", de, ate)).andExpect(status().isOk());
        como(cenario.padreA, get("/api/financeiro/resumo?de={de}&ate={ate}", de, ate)).andExpect(status().isOk());
        for (Usuario u : List.of(cenario.coordenadorPascom, cenario.vicePascom, cenario.secretario1Pascom,
                cenario.tesoureiroPascom, cenario.membro1Pascom, cenario.coordenadorEcc)) {
            como(u, get("/api/financeiro/resumo?de={de}&ate={ate}", de, ate)).andExpect(status().isForbidden());
        }
    }

    @Test
    void verEscalasDaPastoral() throws Exception {
        Celebracao celebracao = criarCelebracaoDireta(LocalDate.now().plusDays(16));
        Vaga vaga = criarVagaDireta(celebracao, 3);

        for (Usuario u : List.of(cenario.padreA, cenario.coordenadorPascom, cenario.vicePascom,
                cenario.secretario1Pascom, cenario.tesoureiroPascom, cenario.membro1Pascom)) {
            como(u, get("/api/vagas/{id}", vaga.getId())).andExpect(status().isOk());
        }
        comoAdmin(get("/api/vagas/{id}", vaga.getId())).andExpect(status().isOk());
        como(cenario.coordenadorEcc, get("/api/vagas/{id}", vaga.getId())).andExpect(status().isNotFound());
    }

    @Test
    void rotasPlataforma() throws Exception {
        comoAdmin(get("/api/plataforma/resumo")).andExpect(status().isOk());
        for (Usuario u : List.of(cenario.padreA, cenario.coordenadorPascom, cenario.vicePascom, cenario.secretario1Pascom,
                cenario.tesoureiroPascom, cenario.membro1Pascom, cenario.coordenadorEcc)) {
            como(u, get("/api/plataforma/resumo")).andExpect(status().isForbidden());
        }
    }
}
