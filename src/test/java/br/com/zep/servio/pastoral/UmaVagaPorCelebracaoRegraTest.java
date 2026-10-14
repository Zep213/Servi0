package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.service.escalacao.regra.ContextoElegibilidade;
import br.com.zep.servio.service.escalacao.regra.UmaVagaPorCelebracaoRegra;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UmaVagaPorCelebracaoRegraTest {

    @Mock
    AlocacaoRepository alocacaoRepository;

    @Test
    void codigoEPadroes() {
        UmaVagaPorCelebracaoRegra r = regra();
        assertThat(r.codigo()).isEqualTo("UMA_VAGA_POR_CELEBRACAO");
        assertThat(r.padroes()).isEmpty();
    }

    @Test
    void bloqueiaSeJaOcupaOutraVagaNaMesmaCelebracao() {
        Celebracao celebracao = celebracaoComId(10L);
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(anyLong(), any(), anyLong()))
                .thenReturn(List.of(alocacaoNaCelebracao(celebracao)));

        var resultado = regra().impedimento(candidato(), contexto(celebracao), Map.of());

        assertThat(resultado).isPresent();
    }

    @Test
    void naoBloqueiaSeAlocacaoExistenteEhDeOutraCelebracao() {
        Celebracao celebracaoAlvo = celebracaoComId(10L);
        Celebracao outraCelebracao = celebracaoComId(20L);
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(anyLong(), any(), anyLong()))
                .thenReturn(List.of(alocacaoNaCelebracao(outraCelebracao)));

        var resultado = regra().impedimento(candidato(), contexto(celebracaoAlvo), Map.of());

        assertThat(resultado).isEmpty();
    }

    @Test
    void semAlocacoesNaoBloqueia() {
        when(alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(anyLong(), any(), anyLong()))
                .thenReturn(List.of());

        var resultado = regra().impedimento(candidato(), contexto(celebracaoComId(10L)), Map.of());

        assertThat(resultado).isEmpty();
    }

    private UmaVagaPorCelebracaoRegra regra() {
        return new UmaVagaPorCelebracaoRegra(alocacaoRepository);
    }

    private Usuario candidato() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        return usuario;
    }

    private Celebracao celebracaoComId(Long id) {
        Celebracao celebracao = new Celebracao();
        celebracao.setId(id);
        return celebracao;
    }

    private ContextoElegibilidade contexto(Celebracao celebracao) {
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        return new ContextoElegibilidade(vaga, 0L);
    }

    private Alocacao alocacaoNaCelebracao(Celebracao celebracao) {
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        Alocacao alocacao = new Alocacao();
        alocacao.setVaga(vaga);
        return alocacao;
    }
}
