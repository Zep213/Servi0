package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.security.PastoraisPermissao;
import br.com.zep.servio.security.UsuarioLogado;
import br.com.zep.servio.security.UsuarioPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PastoraisPermissaoTest {

    private static final Long USUARIO_ID = 1L;
    private static final Long PASTORAL_ID = 10L;

    @Mock
    UsuarioPastoralRepository usuarioPastoralRepository;

    @Mock
    UsuarioLogado usuarioLogado;

    private PastoraisPermissao permissao() {
        return new PastoraisPermissao(usuarioPastoralRepository, usuarioLogado);
    }

    private void logadoComo(Perfil perfil) {
        UsuarioPrincipal principal = new UsuarioPrincipal(USUARIO_ID, 100L, "Fulano", "fulano@servio.dev", null, perfil);
        lenient().when(usuarioLogado.get()).thenReturn(principal);
        lenient().when(usuarioLogado.id()).thenReturn(USUARIO_ID);
    }

    private void comPapelReal(PapelPastoral papel) {
        UsuarioPastoral up = new UsuarioPastoral();
        up.setPapel(papel);
        lenient().when(usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(USUARIO_ID, PASTORAL_ID))
                .thenReturn(Optional.of(up));
    }

    @Test
    void ehAdminEEhPadre() {
        logadoComo(Perfil.ADMIN);
        assertThat(permissao().ehAdmin()).isTrue();
        assertThat(permissao().ehPadre()).isFalse();

        logadoComo(Perfil.PADRE);
        assertThat(permissao().ehPadre()).isTrue();
        assertThat(permissao().ehAdmin()).isFalse();
    }

    @Test
    void adminTemQualquerPapelEmQualquerPastoral() {
        logadoComo(Perfil.ADMIN);
        assertThat(permissao().temPapel(PASTORAL_ID, "COORDENADOR")).isTrue();
        assertThat(permissao().temPapel(PASTORAL_ID, "MEMBRO")).isTrue();
    }

    @Test
    void padreTemApenasOPapelCoordenador() {
        logadoComo(Perfil.PADRE);
        when(usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(USUARIO_ID, PASTORAL_ID))
                .thenReturn(Optional.empty());

        assertThat(permissao().temPapel(PASTORAL_ID, "COORDENADOR")).isTrue();
        assertThat(permissao().temPapel(PASTORAL_ID, "MEMBRO")).isFalse();
    }

    @Test
    void servidorComPapelRealCorrespondente() {
        logadoComo(Perfil.SERVIDOR);
        comPapelReal(PapelPastoral.SECRETARIO);

        assertThat(permissao().temPapel(PASTORAL_ID, "SECRETARIO")).isTrue();
        assertThat(permissao().temPapel(PASTORAL_ID, "COORDENADOR")).isFalse();
    }

    @Test
    void servidorSemParticipacaoNaoTemPapelNenhum() {
        logadoComo(Perfil.SERVIDOR);
        when(usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(USUARIO_ID, PASTORAL_ID))
                .thenReturn(Optional.empty());

        assertThat(permissao().temPapel(PASTORAL_ID, "MEMBRO")).isFalse();
    }

    @Test
    void temQualquerPapelVerdadeiroSeUmDelesBater() {
        logadoComo(Perfil.SERVIDOR);
        comPapelReal(PapelPastoral.VICE);

        assertThat(permissao().temQualquerPapel(PASTORAL_ID, "COORDENADOR", "VICE")).isTrue();
        assertThat(permissao().temQualquerPapel(PASTORAL_ID, "COORDENADOR", "TESOUREIRO")).isFalse();
    }

    @Test
    void podeEscreverFinanceiroSoAdminOuTesoureiroReal() {
        logadoComo(Perfil.ADMIN);
        assertThat(permissao().podeEscreverFinanceiro(PASTORAL_ID)).isTrue();

        logadoComo(Perfil.PADRE);
        when(usuarioPastoralRepository.findByUsuarioIdAndPastoralIdAndActiveTrue(USUARIO_ID, PASTORAL_ID))
                .thenReturn(Optional.empty());
        assertThat(permissao().podeEscreverFinanceiro(PASTORAL_ID)).isFalse();

        logadoComo(Perfil.SERVIDOR);
        comPapelReal(PapelPastoral.TESOUREIRO);
        assertThat(permissao().podeEscreverFinanceiro(PASTORAL_ID)).isTrue();

        logadoComo(Perfil.SERVIDOR);
        comPapelReal(PapelPastoral.COORDENADOR);
        assertThat(permissao().podeEscreverFinanceiro(PASTORAL_ID)).isFalse();
    }

    @Test
    void podeLerFinanceiroAdminPadreTesoureiroOuCoordenador() {
        logadoComo(Perfil.ADMIN);
        assertThat(permissao().podeLerFinanceiro(PASTORAL_ID)).isTrue();

        logadoComo(Perfil.PADRE);
        assertThat(permissao().podeLerFinanceiro(PASTORAL_ID)).isTrue();

        logadoComo(Perfil.SERVIDOR);
        comPapelReal(PapelPastoral.TESOUREIRO);
        assertThat(permissao().podeLerFinanceiro(PASTORAL_ID)).isTrue();

        logadoComo(Perfil.SERVIDOR);
        comPapelReal(PapelPastoral.COORDENADOR);
        assertThat(permissao().podeLerFinanceiro(PASTORAL_ID)).isTrue();

        logadoComo(Perfil.SERVIDOR);
        comPapelReal(PapelPastoral.MEMBRO);
        assertThat(permissao().podeLerFinanceiro(PASTORAL_ID)).isFalse();
    }

    @Test
    void pastoraisVisiveisVazioParaAdminEPadre() {
        logadoComo(Perfil.ADMIN);
        assertThat(permissao().pastoraisVisiveis()).isEmpty();

        logadoComo(Perfil.PADRE);
        assertThat(permissao().pastoraisVisiveis()).isEmpty();
    }

    @Test
    void pastoraisVisiveisListaQualquerPapelParaServidor() {
        logadoComo(Perfil.SERVIDOR);
        when(usuarioPastoralRepository.findByUsuarioIdAndActiveTrue(USUARIO_ID))
                .thenReturn(List.of(participacao(1L, PapelPastoral.MEMBRO), participacao(2L, PapelPastoral.COORDENADOR)));

        Optional<List<Long>> visiveis = permissao().pastoraisVisiveis();

        assertThat(visiveis).isPresent();
        assertThat(visiveis.get()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void pastoraisGerenciadasVazioParaAdminEPadre() {
        logadoComo(Perfil.ADMIN);
        assertThat(permissao().pastoraisGerenciadas()).isEmpty();

        logadoComo(Perfil.PADRE);
        assertThat(permissao().pastoraisGerenciadas()).isEmpty();
    }

    @Test
    void pastoraisGerenciadasSoCoordenadorOuVice() {
        logadoComo(Perfil.SERVIDOR);
        when(usuarioPastoralRepository.findByUsuarioIdAndActiveTrue(USUARIO_ID)).thenReturn(List.of(
                participacao(1L, PapelPastoral.COORDENADOR),
                participacao(2L, PapelPastoral.VICE),
                participacao(3L, PapelPastoral.MEMBRO),
                participacao(4L, PapelPastoral.SECRETARIO)));

        Optional<List<Long>> gerenciadas = permissao().pastoraisGerenciadas();

        assertThat(gerenciadas).isPresent();
        assertThat(gerenciadas.get()).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void ehCoordenadorDeAlgumaPastoral() {
        logadoComo(Perfil.SERVIDOR);
        when(usuarioPastoralRepository.existsByUsuarioIdAndPapelAndActiveTrue(USUARIO_ID, PapelPastoral.COORDENADOR))
                .thenReturn(true);

        assertThat(permissao().ehCoordenadorDeAlgumaPastoral()).isTrue();
    }

    private UsuarioPastoral participacao(Long pastoralId, PapelPastoral papel) {
        Pastoral pastoral = new Pastoral();
        pastoral.setId(pastoralId);
        UsuarioPastoral up = new UsuarioPastoral();
        up.setPastoral(pastoral);
        up.setPapel(papel);
        return up;
    }
}
