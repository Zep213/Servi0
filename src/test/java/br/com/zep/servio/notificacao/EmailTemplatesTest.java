package br.com.zep.servio.notificacao;

import br.com.zep.servio.config.EmailTemplateConfig;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Renderiza os templates com os motores reais: os ITs mockam o Notificador e nunca chegam aqui. */
class EmailTemplatesTest {

    private final EmailTemplateConfig config = new EmailTemplateConfig();
    private final TemplateEngine html = config.emailHtmlEngine();
    private final TemplateEngine texto = config.emailTextEngine();

    private static Map<String, Object> variaveis(boolean comOpcionais) {
        Map<String, Object> v = new HashMap<>();
        v.put("assunto", "Assunto de teste");
        v.put("nome", "Maria");
        v.put("titulo", "Missa dominical");
        v.put("data", "11/10/2026");
        v.put("hora", "08:00");
        v.put("funcao", "Comunicação");
        v.put("pastoral", "Pascom");
        v.put("prazo", "09/10/2026 08:00");
        v.put("mensagem", "Mensagem de teste");
        v.put("link", "http://localhost/convite#token-de-teste");
        v.put("convidadoPor", "João");
        v.put("quando", "09/11/2026 10:00");
        v.put("aparelho", "Celular iPhone");
        v.put("coordenacao", false);
        v.put("chegada", comOpcionais ? "07:30" : null);
        v.put("observacao", comOpcionais ? "Levar a câmera" : null);
        return v;
    }

    @ParameterizedTest
    @ValueSource(strings = {"convite", "lembrete-resposta", "lembrete-servico", "substituicao", "aviso-coordenador", "ativacao", "recuperacao", "senha-alterada"})
    void renderizaHtmlETextoComTodasAsVariaveis(String template) {
        Context contexto = new Context();
        contexto.setVariables(variaveis(true));

        assertThat(html.process(template, contexto)).contains("Maria");
        assertThat(texto.process(template, contexto)).contains("Maria");
    }

    @ParameterizedTest
    @ValueSource(strings = {"convite", "lembrete-resposta", "lembrete-servico", "substituicao", "aviso-coordenador", "ativacao", "recuperacao", "senha-alterada"})
    void renderizaSemOsCamposOpcionais(String template) {
        Context contexto = new Context();
        contexto.setVariables(variaveis(false));

        assertThat(html.process(template, contexto)).doesNotContain("Levar a câmera");
        assertThat(texto.process(template, contexto)).doesNotContain("Levar a câmera");
    }

    /** Decisão do projeto: o aviso de senha alterada diz só o tipo de aparelho, sem cidade, IP nem localização. */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void senhaAlteradaNaoFalaDeCidadeIpNemLocalizacao(boolean pelaCoordenacao) {
        Map<String, Object> v = variaveis(true);
        v.put("coordenacao", pelaCoordenacao);
        v.put("aparelho", pelaCoordenacao ? null : "Celular iPhone");
        Context contexto = new Context();
        contexto.setVariables(v);

        for (String saida : new String[] {html.process("senha-alterada", contexto), texto.process("senha-alterada", contexto)}) {
            assertThat(saida.toLowerCase()).doesNotContain("cidade", "localiza", "endereço ip");
            assertThat(saida).doesNotContainPattern("\\bIP\\b");
            assertThat(saida).contains(pelaCoordenacao ? "coordenação da paróquia" : "Celular iPhone");
        }
    }
}
