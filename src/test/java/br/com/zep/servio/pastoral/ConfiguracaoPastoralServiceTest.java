package br.com.zep.servio.pastoral;

import br.com.zep.servio.model.PastoralConfig;
import br.com.zep.servio.repository.PastoralConfigRepository;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import br.com.zep.servio.service.escalacao.regra.RegraElegibilidade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfiguracaoPastoralServiceTest {

    @Mock
    PastoralConfigRepository pastoralConfigRepository;

    @Mock
    RegraElegibilidade regra;

    private ConfiguracaoPastoralService service() {
        return new ConfiguracaoPastoralService(List.of(regra), pastoralConfigRepository);
    }

    @Test
    void catalogoDevolveARegrasInjetadas() {
        assertThat(service().catalogo()).containsExactly(regra);
    }

    @Test
    void ativaPadraoVerdadeiroQuandoPastoralNaoConfigurou() {
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "REGRA_X")).thenReturn(Optional.empty());

        assertThat(service().ativa(1L, "REGRA_X")).isTrue();
    }

    @Test
    void ativaFalsoQuandoPastoralDesligouExplicitamente() {
        PastoralConfig config = configComAtiva(false);
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "REGRA_X")).thenReturn(Optional.of(config));

        assertThat(service().ativa(1L, "REGRA_X")).isFalse();
    }

    @Test
    void ativaVerdadeiroQuandoPastoralConfigurouExplicitamenteLigada() {
        PastoralConfig config = configComAtiva(true);
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "REGRA_X")).thenReturn(Optional.of(config));

        assertThat(service().ativa(1L, "REGRA_X")).isTrue();
    }

    @Test
    void parametrosUsaPadraoDaRegraQuandoNaoConfigurado() {
        when(regra.codigo()).thenReturn("REGRA_X");
        when(regra.padroes()).thenReturn(Map.of("horas", 48));
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "REGRA_X")).thenReturn(Optional.empty());

        assertThat(service().parametros(1L, regra)).containsEntry("horas", 48);
    }

    @Test
    void parametrosSobrescritosPelaPastoral() {
        when(regra.codigo()).thenReturn("REGRA_X");
        Map<String, Object> parametrosCustom = Map.of("horas", 72);
        PastoralConfig config = configComAtiva(true);
        config.setParametros(new LinkedHashMap<>(parametrosCustom));
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "REGRA_X")).thenReturn(Optional.of(config));

        assertThat(service().parametros(1L, regra)).containsEntry("horas", 72);
    }

    @Test
    void prazoRespostaPadraoDe24Horas() {
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "PRAZO_RESPOSTA")).thenReturn(Optional.empty());

        assertThat(service().prazoRespostaHoras(1L)).isEqualTo(24L);
    }

    @Test
    void prazoRespostaSobrescritoPara72Horas() {
        PastoralConfig config = configComAtiva(true);
        config.setParametros(new LinkedHashMap<>(Map.of("horas", 72)));
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "PRAZO_RESPOSTA")).thenReturn(Optional.of(config));

        assertThat(service().prazoRespostaHoras(1L)).isEqualTo(72L);
    }

    @Test
    void coberturaAutomaticaPadraoDesligada() {
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "COBERTURA_AUTOMATICA")).thenReturn(Optional.empty());

        assertThat(service().coberturaAutomaticaLigada(1L)).isFalse();
    }

    @Test
    void coberturaAutomaticaLigadaQuandoConfiguradaAtiva() {
        PastoralConfig config = configComAtiva(true);
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "COBERTURA_AUTOMATICA")).thenReturn(Optional.of(config));

        assertThat(service().coberturaAutomaticaLigada(1L)).isTrue();
    }

    @Test
    void coberturaAutomaticaDesligadaQuandoConfiguradaInativa() {
        PastoralConfig config = configComAtiva(false);
        when(pastoralConfigRepository.findByPastoralIdAndChaveAndActiveTrue(1L, "COBERTURA_AUTOMATICA")).thenReturn(Optional.of(config));

        assertThat(service().coberturaAutomaticaLigada(1L)).isFalse();
    }

    private PastoralConfig configComAtiva(boolean ativa) {
        PastoralConfig config = new PastoralConfig();
        config.setAtiva(ativa);
        return config;
    }
}
