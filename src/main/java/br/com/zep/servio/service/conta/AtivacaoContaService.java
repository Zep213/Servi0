package br.com.zep.servio.service.conta;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.AtivacaoDetalhesDTO;
import br.com.zep.servio.model.enumerated.TipoTokenConta;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.service.AuditLogService;
import br.com.zep.servio.util.EmailMascarado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Ativação pelo link do e-mail, sem login: o token é a credencial (Sprint 2, Cartão 2). */
@Service
@RequiredArgsConstructor
public class AtivacaoContaService {

    private final TokenContaService tokenContaService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final Clock clock;

    /** Não altera nada: serve à tela "Olá, Maria" antes de pedir a senha. */
    @Transactional(readOnly = true)
    public AtivacaoDetalhesDTO detalhes(String token) {
        Usuario usuario = tokenContaService.consultar(token, TipoTokenConta.ATIVACAO);
        if (usuario.estaAtivada()) {
            throw new TokenContaInvalidoException();
        }
        return new AtivacaoDetalhesDTO(CadastroMembroService.primeiroNome(usuario.getNome()),
                EmailMascarado.mascarar(usuario.getEmail()));
    }

    /**
     * Consome o token e grava o hash da senha. Não abre sessão: a pessoa entra em seguida, pela tela de
     * login. Se a conta já estava ativada, a transação desfaz o consumo e a resposta é a 404 genérica.
     */
    @Transactional
    public void ativar(String token, String senha) {
        Usuario usuario = tokenContaService.consumir(token, TipoTokenConta.ATIVACAO);
        if (usuario.estaAtivada()) {
            throw new TokenContaInvalidoException();
        }
        usuario.setSenha(passwordEncoder.encode(senha));
        usuario.setAtivadoEm(LocalDateTime.now(clock));
        usuarioRepository.save(usuario);
        auditLogService.registrar("CONTA_ATIVADA", usuario.getId(), usuario, usuario.getParoquiaId(), null);
    }
}
