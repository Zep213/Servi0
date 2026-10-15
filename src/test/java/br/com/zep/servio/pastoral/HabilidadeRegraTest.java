package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.Funcao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.repository.UsuarioFuncaoRepository;
import br.com.zep.servio.service.escalacao.regra.ContextoElegibilidade;
import br.com.zep.servio.service.escalacao.regra.HabilidadeRegra;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HabilidadeRegraTest {

    private final UsuarioFuncaoRepository repositorio = mock(UsuarioFuncaoRepository.class);
    private final HabilidadeRegra regra = new HabilidadeRegra(repositorio, true);

    private final Usuario candidato = usuario(7L);
    private final ContextoElegibilidade contexto = contextoComFuncao(30L);

    private static Usuario usuario(Long id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        return usuario;
    }

    private static ContextoElegibilidade contextoComFuncao(Long funcaoId) {
        Funcao funcao = new Funcao();
        funcao.setId(funcaoId);
        Vaga vaga = new Vaga();
        vaga.setFuncao(funcao);
        return new ContextoElegibilidade(vaga, 0L);
    }

    @Test
    void comAHabilidadeAtivaNaoHaImpedimento() {
        when(repositorio.existsByUsuarioIdAndFuncaoIdAndActiveTrue(7L, 30L)).thenReturn(true);

        assertThat(regra.impedimento(candidato, contexto, Map.of())).isEmpty();
    }

    @Test
    void semAHabilidadeOuComEladesmarcadaHaImpedimento() {
        // o repositório só conta habilidade ATIVA: desmarcada devolve false, igual a nunca ter tido
        when(repositorio.existsByUsuarioIdAndFuncaoIdAndActiveTrue(7L, 30L)).thenReturn(false);

        assertThat(regra.impedimento(candidato, contexto, Map.of())).isEqualTo(Optional.of("Não tem a habilidade desta vaga"));
    }

    @Test
    void identificacaoEPadraoDaRegra() {
        assertThat(regra.codigo()).isEqualTo("HABILIDADE");
        assertThat(regra.padroes()).isEmpty();
        assertThat(regra.ligadaPorPadrao()).isTrue();
        assertThat(new HabilidadeRegra(repositorio, false).ligadaPorPadrao()).isFalse();
    }
}
