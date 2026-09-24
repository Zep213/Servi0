package br.com.zep.servio.security;

import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Ponto único de decisão de autorização dentro de uma pastoral (Parte 2.3).
 * A matriz do SecurityConfig cobre só a barreira geral (Perfil da conta); esta
 * classe é quem decide de fato, usada em @PreAuthorize e direto nos services.
 */
@Component("pastorais")
@RequiredArgsConstructor
public class PastoraisPermissao {

    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final UsuarioLogado usuarioLogado;

    public boolean ehAdmin() {
        return usuarioLogado.get().getPerfil() == Perfil.ADMIN;
    }

    public boolean ehPadre() {
        return usuarioLogado.get().getPerfil() == Perfil.PADRE;
    }

    /**
     * Verdadeiro se o usuário tem este papel na pastoral. ADMIN tem qualquer papel em
     * qualquer pastoral; PADRE tem o papel COORDENADOR em qualquer pastoral da própria
     * paróquia (ele indica o coordenador de cada uma, então pode agir como um).
     */
    public boolean temPapel(Long pastoralId, String papel) {
        if (ehAdmin()) {
            return true;
        }
        PapelPastoral alvo = PapelPastoral.valueOf(papel);
        if (ehPadre() && alvo == PapelPastoral.COORDENADOR) {
            return true;
        }
        return temPapelReal(pastoralId, alvo);
    }

    public boolean temQualquerPapel(Long pastoralId, String... papeis) {
        for (String papel : papeis) {
            if (temPapel(pastoralId, papel)) {
                return true;
            }
        }
        return false;
    }

    /** Escrita no financeiro é mais estrita que a checagem geral: padre só lê, não lança. */
    public boolean podeEscreverFinanceiro(Long pastoralId) {
        return ehAdmin() || temPapelReal(pastoralId, PapelPastoral.TESOUREIRO);
    }

    public boolean podeLerFinanceiro(Long pastoralId) {
        return ehAdmin() || ehPadre()
                || temPapelReal(pastoralId, PapelPastoral.TESOUREIRO)
                || temPapelReal(pastoralId, PapelPastoral.COORDENADOR);
    }

    /**
     * Ids das pastorais em que o usuário tem algum papel, para filtrar listagens.
     * Vazio (Optional.empty) significa "sem restrição": todas, o caso de PADRE/ADMIN.
     */
    public Optional<List<Long>> pastoraisVisiveis() {
        if (ehAdmin() || ehPadre()) {
            return Optional.empty();
        }
        List<Long> ids = usuarioPastoralRepository.findByUsuarioIdAndActiveTrue(usuarioLogado.id())
                .stream().map(up -> up.getPastoral().getId()).distinct().toList();
        return Optional.of(ids);
    }

    public boolean ehCoordenadorDeAlgumaPastoral() {
        return usuarioPastoralRepository.existsByUsuarioIdAndPapelAndActiveTrue(usuarioLogado.id(), PapelPastoral.COORDENADOR);
    }

    private boolean temPapelReal(Long pastoralId, PapelPastoral papel) {
        return usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(usuarioLogado.id(), pastoralId)
                .map(UsuarioPastoral::getPapel)
                .map(p -> p == papel)
                .orElse(false);
    }
}
