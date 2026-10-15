package br.com.zep.servio.service.conta;

import br.com.zep.servio.model.TokenConta;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.TipoTokenConta;
import br.com.zep.servio.repository.TokenContaRepository;
import br.com.zep.servio.service.escalacao.TokenConvite;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Tokens de ativação, recuperação de senha e proteção da conta. O token puro é devolvido só a quem
 * emite (para montar o link do e-mail) e nunca é gravado nem logado: o banco guarda o SHA-256, igual ao convite.
 */
@Service
public class TokenContaService {

    private final TokenContaRepository repository;
    private final TokenConvite tokenConvite;
    private final Clock clock;
    private final Duration prazoAtivacao;
    private final Duration prazoRecuperacao;
    private final Duration prazoProtecao;

    public TokenContaService(TokenContaRepository repository, TokenConvite tokenConvite, Clock clock,
                             @Value("${servio.conta.ativacao-dias:7}") long ativacaoDias,
                             @Value("${servio.conta.recuperacao-minutos:60}") long recuperacaoMinutos,
                             @Value("${servio.conta.protecao-dias:7}") long protecaoDias) {
        this.repository = repository;
        this.tokenConvite = tokenConvite;
        this.clock = clock;
        this.prazoAtivacao = Duration.ofDays(ativacaoDias);
        this.prazoRecuperacao = Duration.ofMinutes(recuperacaoMinutos);
        this.prazoProtecao = Duration.ofDays(protecaoDias);
    }

    /** Apaga o pendente do mesmo tipo (o link antigo morre), grava o novo e devolve o token puro. */
    @Transactional
    public String emitir(Usuario usuario, TipoTokenConta tipo) {
        repository.apagarPendentes(usuario.getId(), tipo);
        String token = tokenConvite.gerar();
        LocalDateTime agora = LocalDateTime.now(clock);
        TokenConta registro = new TokenConta();
        registro.setUsuario(usuario);
        registro.setTipo(tipo);
        registro.setTokenHash(tokenConvite.hash(token));
        registro.setCriadoEm(agora);
        registro.setExpiraEm(agora.plus(prazoDe(tipo)));
        repository.save(registro);
        return token;
    }

    /**
     * Usa o token uma única vez e devolve o usuário (ativo). Inexistente, usado, de outro tipo ou de
     * usuário inativo: {@link TokenContaInvalidoException}. Passou do prazo: {@link TokenContaVencidoException}.
     * Quem decide o consumo é o UPDATE condicional: de duas chamadas simultâneas só uma passa.
     */
    @Transactional
    public Usuario consumir(String token, TipoTokenConta tipo) {
        String hash = tokenConvite.hash(token);
        TokenConta registro = repository.buscarPorHash(hash)
                .filter(t -> t.getTipo() == tipo && t.getUsadoEm() == null && t.getUsuario().isActive())
                .orElseThrow(TokenContaInvalidoException::new);
        LocalDateTime agora = LocalDateTime.now(clock);
        if (!registro.getExpiraEm().isAfter(agora)) {
            throw new TokenContaVencidoException();
        }
        if (repository.consumir(hash, tipo, agora) != 1) {
            throw new TokenContaInvalidoException();
        }
        return registro.getUsuario();
    }

    /** Apaga todos os pendentes da pessoa, de qualquer tipo (usado quando a senha muda). */
    @Transactional
    public void invalidar(Usuario usuario) {
        repository.apagarPendentes(usuario.getId());
    }

    private Duration prazoDe(TipoTokenConta tipo) {
        return switch (tipo) {
            case ATIVACAO -> prazoAtivacao;
            case RECUPERACAO -> prazoRecuperacao;
            case PROTECAO -> prazoProtecao;
        };
    }
}
