package br.com.zep.servio.service.conta;

import br.com.zep.servio.exception.RecursoNaoEncontradoException;
import br.com.zep.servio.exception.RegraNegocioException;
import br.com.zep.servio.exception.ServioException;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.dto.MembroCriadoDTO;
import br.com.zep.servio.model.dto.MembroNovoRequestDTO;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.TipoTokenConta;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.service.AuditLogService;
import br.com.zep.servio.service.UsuarioPastoralService;
import br.com.zep.servio.service.UsuarioService;
import br.com.zep.servio.service.notification.FilaEmail;
import br.com.zep.servio.util.EmailMascarado;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Cadastro de membro numa operação só (Sprint 2, Cartão 2): conta sem senha, papel na pastoral, token de
 * ativação e e-mail na fila, tudo na mesma transação. Se qualquer parte falha, nada fica gravado (nem conta
 * órfã, nem e-mail na fila). Quem pode cadastrar é decidido pelas mesmas regras de hoje, nos services de
 * usuário e de papel; esta classe só as encadeia.
 */
@Service
@RequiredArgsConstructor
public class CadastroMembroService {

    private static final DateTimeFormatter PRAZO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Duration INTERVALO_REENVIO = Duration.ofHours(1);

    private final UsuarioService usuarioService;
    private final UsuarioPastoralService usuarioPastoralService;
    private final UsuarioRepository usuarioRepository;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final PastoralRepository pastoralRepository;
    private final TokenContaService tokenContaService;
    private final FilaEmail filaEmail;
    private final AuditLogService auditLogService;
    private final UsuarioLogado usuarioLogado;
    private final Clock clock;

    @Value("${servio.front-url}")
    private String frontUrl;

    @Transactional
    public MembroCriadoDTO cadastrar(Long pastoralId, MembroNovoRequestDTO request) {
        PapelPastoral papel = request.papel() != null ? request.papel() : PapelPastoral.MEMBRO;
        Pastoral pastoral = pastoral(pastoralId);
        // antes de criar qualquer coisa: pastoral fora do alcance dá 404, sem permissão dá 403
        usuarioPastoralService.exigirGestor(pastoralId, papel);

        Usuario usuario = usuarioService.criarSemSenha(request.nome(), request.email());
        UsuarioPastoral participacao = usuarioPastoralService.criarParaConta(usuario, pastoralId, papel);
        enviarAtivacao(usuario, pastoral);

        Usuario autor = usuarioRepository.findById(usuarioLogado.id()).orElseThrow();
        auditLogService.registrar("MEMBRO_CADASTRADO", usuario.getId(), autor, usuario.getParoquiaId(),
                "Papel " + papel + " na pastoral " + pastoral.getNome());
        return new MembroCriadoDTO(usuario.getId(), participacao.getId(), EmailMascarado.mascarar(usuario.getEmail()));
    }

    /** Link novo; o antigo morre de propósito (como no reenvio manual do convite). No máximo um por hora. */
    @Transactional
    public void reenviarAtivacao(Long pastoralId, Long usuarioId) {
        Pastoral pastoral = pastoral(pastoralId);
        usuarioPastoralService.exigirGestor(pastoralId, PapelPastoral.MEMBRO);

        usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(usuarioId, pastoralId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membro", usuarioId));
        Usuario usuario = usuarioRepository.findByIdAndParoquiaIdAndActiveTrue(usuarioId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Membro", usuarioId));
        if (usuario.estaAtivada()) {
            throw new RegraNegocioException("Esta conta já foi ativada");
        }
        LocalDateTime limite = LocalDateTime.now(clock).minus(INTERVALO_REENVIO);
        if (tokenContaService.emitidoEm(usuario, TipoTokenConta.ATIVACAO).map(em -> em.isAfter(limite)).orElse(false)) {
            throw new ServioException("Esta ativação já foi reenviada na última hora", HttpStatus.TOO_MANY_REQUESTS);
        }
        enviarAtivacao(usuario, pastoral);
    }

    private Pastoral pastoral(Long pastoralId) {
        return pastoralRepository.findByIdAndParoquiaIdAndActiveTrue(pastoralId, usuarioLogado.paroquiaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pastoral", pastoralId));
    }

    private void enviarAtivacao(Usuario usuario, Pastoral pastoral) {
        String token = tokenContaService.emitir(usuario, TipoTokenConta.ATIVACAO);
        LocalDateTime prazo = LocalDateTime.now(clock).plus(tokenContaService.prazoDe(TipoTokenConta.ATIVACAO));

        String assunto = "Ative sua conta no Servio";
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("assunto", assunto);
        variaveis.put("nome", primeiroNome(usuario.getNome()));
        variaveis.put("pastoral", pastoral.getNome());
        variaveis.put("convidadoPor", primeiroNome(usuarioLogado.get().getNome()));
        variaveis.put("prazo", prazo.format(PRAZO));
        variaveis.put("link", frontUrl + "/ativar#" + token);
        // dentro da transação: o e-mail só sai se o cadastro comitar
        filaEmail.enfileirar(usuario.getParoquiaId(), usuario.getEmail(), assunto, "ativacao", variaveis);
    }

    static String primeiroNome(String nomeCompleto) {
        String limpo = nomeCompleto == null ? "" : nomeCompleto.trim();
        int espaco = limpo.indexOf(' ');
        return espaco < 0 ? limpo : limpo.substring(0, espaco);
    }
}
