package br.com.zep.servio.security;

import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checagem adicional de papel dentro de uma pastoral, usada em @PreAuthorize
 * (ex.: @PreAuthorize("@pastorais.temPapel(#pastoralId, 'COORDENADOR')")).
 * A matriz do SecurityConfig cobre só a barreira geral (perfil da paróquia);
 * esta classe é a checagem fina de papel dentro da pastoral.
 */
@Component("pastorais")
@RequiredArgsConstructor
public class PastoraisPermissao {

    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final UsuarioLogado usuarioLogado;

    public boolean temPapel(Long pastoralId, String papel) {
        return usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(usuarioLogado.id(), pastoralId)
                .map(up -> up.getPapel() == PapelPastoral.valueOf(papel))
                .orElse(false);
    }

    public boolean temQualquerPapel(Long pastoralId, String... papeis) {
        for (String papel : papeis) {
            if (temPapel(pastoralId, papel)) {
                return true;
            }
        }
        return false;
    }
}
