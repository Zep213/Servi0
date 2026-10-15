package br.com.zep.servio.service.conta;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.TipoTokenConta;
import br.com.zep.servio.service.notification.FilaEmail;
import br.com.zep.servio.util.AparelhoUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Aviso de senha alterada (Sprint 2, Cartão 4), com o link de "Proteger minha conta" (token {@code PROTECAO}).
 * Só o tipo de aparelho entra no e-mail: nada de cidade, IP nem User-Agent. A senha nunca vai nas variáveis.
 * Dentro da transação de quem chama, o e-mail só sai se a troca da senha comitar.
 */
@Service
@RequiredArgsConstructor
public class ContaAvisoService {

    private static final DateTimeFormatter QUANDO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TokenContaService tokenContaService;
    private final FilaEmail filaEmail;
    private final Clock clock;

    @Value("${servio.front-url}")
    private String frontUrl;

    /** A própria pessoa trocou (ou redefiniu) a senha: o aviso diz de que tipo de aparelho. */
    @Transactional
    public void avisarSenhaAlterada(Usuario usuario, String userAgent) {
        enfileirar(usuario, AparelhoUsuario.descrever(userAgent), false);
    }

    /** PADRE/ADMIN trocou a senha da conta: não há "aparelho" da pessoa, o e-mail diz que foi a coordenação. */
    @Transactional
    public void avisarSenhaAlteradaPelaCoordenacao(Usuario usuario) {
        enfileirar(usuario, null, true);
    }

    private void enfileirar(Usuario usuario, String aparelho, boolean coordenacao) {
        String token = tokenContaService.emitir(usuario, TipoTokenConta.PROTECAO);
        String assunto = "Sua senha do Servio foi alterada";
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("assunto", assunto);
        variaveis.put("nome", CadastroMembroService.primeiroNome(usuario.getNome()));
        variaveis.put("quando", LocalDateTime.now(clock).format(QUANDO));
        variaveis.put("aparelho", aparelho);
        variaveis.put("coordenacao", coordenacao);
        variaveis.put("link", frontUrl + "/proteger-conta#" + token);
        filaEmail.enfileirar(usuario.getParoquiaId(), usuario.getEmail(), assunto, "senha-alterada", variaveis);
    }
}
