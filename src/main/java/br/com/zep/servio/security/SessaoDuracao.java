package br.com.zep.servio.security;

import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Quanto tempo a sessão dura sem uso. Membro (perfil SERVIDOR só com papel MEMBRO ou REDES_SOCIAIS, ou sem
 * papel nenhum) fica logado por mais tempo; quem gerencia (qualquer outro papel, PADRE ou ADMIN) por menos.
 */
@Component
public class SessaoDuracao {

    private static final Set<PapelPastoral> PAPEIS_DE_MEMBRO = Set.of(PapelPastoral.MEMBRO, PapelPastoral.REDES_SOCIAIS);

    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final Duration membro;
    private final Duration gestao;

    public SessaoDuracao(UsuarioPastoralRepository usuarioPastoralRepository,
                         @Value("${servio.sessao.membro-dias:60}") int membroDias,
                         @Value("${servio.sessao.gestao-dias:7}") int gestaoDias) {
        this.usuarioPastoralRepository = usuarioPastoralRepository;
        this.membro = Duration.ofDays(membroDias);
        this.gestao = Duration.ofDays(gestaoDias);
    }

    public Duration para(Long usuarioId, Perfil perfil) {
        if (perfil != Perfil.SERVIDOR) {
            return gestao;
        }
        boolean soMembro = usuarioPastoralRepository.findByUsuarioIdAndActiveTrue(usuarioId).stream()
                .map(UsuarioPastoral::getPapel)
                .allMatch(PAPEIS_DE_MEMBRO::contains);
        return soMembro ? membro : gestao;
    }

    /** Maior prazo possível: serve de validade do cookie, quem manda de verdade é a sessão no banco. */
    public Duration maxima() {
        return membro.compareTo(gestao) >= 0 ? membro : gestao;
    }
}
