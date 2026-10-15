package br.com.zep.servio.service.conta;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.RecuperacaoDetalhesDTO;
import br.com.zep.servio.model.enumerated.TipoTokenConta;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.ChaveLogin;
import br.com.zep.servio.security.SessaoService;
import br.com.zep.servio.security.TentativasLogin;
import br.com.zep.servio.security.UsuarioDetailsService;
import br.com.zep.servio.service.AuditLogService;
import br.com.zep.servio.service.notification.FilaEmail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * "Esqueci a senha" (Sprint 2, Cartão 3). O pedido nunca revela se a conta existe: quem chama responde 202 igual
 * em todos os casos e este serviço só envia o e-mail quando há conta ativa, já ativada e dentro do limite.
 * Nada aqui loga o texto digitado, o token nem a senha.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecuperacaoSenhaService {

    private final UsuarioDetailsService usuarios;
    private final TokenContaService tokenContaService;
    private final LimiteRecuperacao limiteRecuperacao;
    private final FilaEmail filaEmail;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessaoService sessaoService;
    private final TentativasLogin tentativas;
    private final ChaveLogin chaveLogin;
    private final AuditLogService auditLogService;

    @Value("${servio.front-url}")
    private String frontUrl;

    /**
     * {@code login} é e-mail ou telefone (achado como no login). Conta inexistente, desativada, ainda não ativada
     * ou acima do limite por hora: não faz nada. O e-mail vai para o endereço da conta, nunca para o digitado.
     */
    @Transactional
    public void solicitar(String login) {
        usuarios.buscar(login)
                .filter(Usuario::estaAtivada)
                .filter(usuario -> limiteRecuperacao.permitir(usuario.getId()))
                .ifPresent(this::enviar);
    }

    /** Não altera nada: a tela só confere se o link ainda vale antes de pedir a senha nova. */
    @Transactional(readOnly = true)
    public RecuperacaoDetalhesDTO detalhes(String token) {
        Usuario usuario = tokenContaService.consultar(token, TipoTokenConta.RECUPERACAO);
        return new RecuperacaoDetalhesDTO(CadastroMembroService.primeiroNome(usuario.getNome()));
    }

    /**
     * Consome o link, grava a senha nova e derruba as sessões abertas. Não abre sessão nova. Limpa o bloqueio de
     * login da conta (quem errou 5 vezes e por isso pediu o link não espera os 15 minutos).
     */
    @Transactional
    public void redefinir(String token, String senha) {
        Usuario usuario = tokenContaService.consumir(token, TipoTokenConta.RECUPERACAO);
        usuario.setSenha(passwordEncoder.encode(senha));
        usuarioRepository.save(usuario);
        tokenContaService.invalidar(usuario);
        sessaoService.encerrarTodas(usuario.getEmail());
        chaveLogin.chavesDaConta(usuario).forEach(tentativas::limpar);
        auditLogService.registrar("SENHA_REDEFINIDA", usuario.getId(), usuario, usuario.getParoquiaId(), null);
    }

    private void enviar(Usuario usuario) {
        String token = tokenContaService.emitir(usuario, TipoTokenConta.RECUPERACAO);
        String assunto = "Redefina sua senha no Servio";
        Map<String, Object> variaveis = new HashMap<>();
        variaveis.put("assunto", assunto);
        variaveis.put("nome", CadastroMembroService.primeiroNome(usuario.getNome()));
        variaveis.put("prazo", descrever(tokenContaService.prazoDe(TipoTokenConta.RECUPERACAO)));
        variaveis.put("link", frontUrl + "/redefinir-senha#" + token);
        filaEmail.enfileirar(usuario.getParoquiaId(), usuario.getEmail(), assunto, "recuperacao", variaveis);
    }

    private static String descrever(Duration prazo) {
        long minutos = prazo.toMinutes();
        if (minutos % 60 == 0) {
            long horas = minutos / 60;
            return horas == 1 ? "1 hora" : horas + " horas";
        }
        return minutos + " minutos";
    }
}
