package br.com.zep.servio.service.conta;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.dto.RecuperacaoDetalhesDTO;
import br.com.zep.servio.model.enumerated.TipoTokenConta;
import br.com.zep.servio.repository.UsuarioRepository;
import br.com.zep.servio.security.SessaoService;
import br.com.zep.servio.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Proteger minha conta" (Sprint 2, Cartão 4): a pessoa não reconhece a troca de senha do e-mail. Sem login: o
 * token do e-mail é a credencial. Zera a senha (a conta fica "protegida": ativada, mas sem senha), derruba as
 * sessões e manda um link novo de recuperação, para definir a senha por um caminho que quem trocou a senha não
 * tem (a caixa de e-mail da pessoa).
 */
@Service
@RequiredArgsConstructor
public class ProtecaoContaService {

    private final TokenContaService tokenContaService;
    private final RecuperacaoSenhaService recuperacaoSenhaService;
    private final UsuarioRepository usuarioRepository;
    private final SessaoService sessaoService;
    private final AuditLogService auditLogService;

    /** Não altera nada: a tela só confere se o link vale e cumprimenta pelo primeiro nome. */
    @Transactional(readOnly = true)
    public RecuperacaoDetalhesDTO detalhes(String token) {
        Usuario usuario = tokenContaService.consultar(token, TipoTokenConta.PROTECAO);
        return new RecuperacaoDetalhesDTO(CadastroMembroService.primeiroNome(usuario.getNome()));
    }

    @Transactional
    public void proteger(String token) {
        Usuario usuario = tokenContaService.consumir(token, TipoTokenConta.PROTECAO);
        usuario.setSenha(null);                       // mantém ativado_em: é conta protegida, não conta nova
        usuarioRepository.save(usuario);
        tokenContaService.invalidar(usuario);          // nenhum outro link (inclusive o de recuperação antigo) vale
        sessaoService.encerrarTodas(usuario.getEmail());
        recuperacaoSenhaService.enviar(usuario);       // link novo de 1 hora para o e-mail da conta
        auditLogService.registrar("CONTA_PROTEGIDA", usuario.getId(), usuario, usuario.getParoquiaId(), null);
    }
}
