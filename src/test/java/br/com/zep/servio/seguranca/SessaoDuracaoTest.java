package br.com.zep.servio.seguranca;

import br.com.zep.servio.security.SessaoDuracao;

import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.enumerated.PapelPastoral;
import br.com.zep.servio.model.enumerated.Perfil;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessaoDuracaoTest {

    private final UsuarioPastoralRepository repo = mock(UsuarioPastoralRepository.class);
    private final SessaoDuracao duracao = new SessaoDuracao(repo, 60, 7);

    private void papeis(PapelPastoral... papeis) {
        List<UsuarioPastoral> lista = java.util.Arrays.stream(papeis).map(p -> {
            UsuarioPastoral up = new UsuarioPastoral();
            up.setPapel(p);
            return up;
        }).toList();
        when(repo.findByUsuarioIdAndActiveTrue(1L)).thenReturn(lista);
    }

    @Test
    void membroERedesSociaisFicamSessentaDias() {
        papeis(PapelPastoral.MEMBRO, PapelPastoral.REDES_SOCIAIS);
        assertEquals(Duration.ofDays(60), duracao.para(1L, Perfil.SERVIDOR));
    }

    @Test
    void semPapelNenhumContaComoMembro() {
        papeis();
        assertEquals(Duration.ofDays(60), duracao.para(1L, Perfil.SERVIDOR));
    }

    @Test
    void qualquerPapelDeGestaoFicaSeteDias() {
        for (PapelPastoral papel : List.of(PapelPastoral.COORDENADOR, PapelPastoral.VICE, PapelPastoral.SECRETARIO,
                PapelPastoral.TESOUREIRO, PapelPastoral.TECNICO)) {
            papeis(PapelPastoral.MEMBRO, papel);
            assertEquals(Duration.ofDays(7), duracao.para(1L, Perfil.SERVIDOR), papel.name());
        }
    }

    @Test
    void padreEAdminFicamSeteDiasSemConsultarPapeis() {
        assertEquals(Duration.ofDays(7), duracao.para(1L, Perfil.PADRE));
        assertEquals(Duration.ofDays(7), duracao.para(1L, Perfil.ADMIN));
    }

    @Test
    void cookieUsaOMaiorPrazo() {
        assertEquals(Duration.ofDays(60), duracao.maxima());
        assertEquals(Duration.ofDays(30), new SessaoDuracao(repo, 5, 30).maxima());
    }
}
