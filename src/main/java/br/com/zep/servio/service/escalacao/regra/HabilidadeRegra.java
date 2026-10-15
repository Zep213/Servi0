package br.com.zep.servio.service.escalacao.regra;

import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.repository.UsuarioFuncaoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * A pessoa precisa ter a habilidade da vaga: a função da vaga tem de estar marcada para ela
 * ({@code usuario_funcao} ativo). Vale para a escalação manual, para a lista de candidatos e para o sorteio.
 *
 * <p>Liga por padrão em toda pastoral (como as outras regras) e a pastoral pode desligar em
 * {@code PUT /api/pastorais/{id}/config/HABILIDADE}. O interruptor {@code servio.escalacao.habilidade-ligada-por-padrao}
 * só muda o padrão de quem nunca configurou: em produção fica {@code true}; os testes que não tratam de habilidade
 * o desligam para não precisar cadastrar habilidade de todo candidato que montam.
 */
@Component
public class HabilidadeRegra implements RegraElegibilidade {

    public static final String CODIGO = "HABILIDADE";

    private final UsuarioFuncaoRepository usuarioFuncaoRepository;
    private final boolean ligadaPorPadrao;

    public HabilidadeRegra(UsuarioFuncaoRepository usuarioFuncaoRepository,
                           @Value("${servio.escalacao.habilidade-ligada-por-padrao:true}") boolean ligadaPorPadrao) {
        this.usuarioFuncaoRepository = usuarioFuncaoRepository;
        this.ligadaPorPadrao = ligadaPorPadrao;
    }

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String descricao() {
        return "A pessoa precisa ter a habilidade (função) da vaga";
    }

    @Override
    public Map<String, Object> padroes() {
        return Map.of();
    }

    @Override
    public boolean ligadaPorPadrao() {
        return ligadaPorPadrao;
    }

    @Override
    public Optional<String> impedimento(Usuario candidato, ContextoElegibilidade contexto, Map<String, Object> parametros) {
        boolean temAHabilidade = usuarioFuncaoRepository.existsByUsuarioIdAndFuncaoIdAndActiveTrue(
                candidato.getId(), contexto.vaga().getFuncao().getId());
        return temAHabilidade ? Optional.empty() : Optional.of("Não tem a habilidade desta vaga");
    }
}
