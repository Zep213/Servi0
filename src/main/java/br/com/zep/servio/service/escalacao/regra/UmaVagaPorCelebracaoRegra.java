package br.com.zep.servio.service.escalacao.regra;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/** Candidato não pode ocupar duas vagas na mesma celebração. */
@Component
@RequiredArgsConstructor
public class UmaVagaPorCelebracaoRegra implements RegraElegibilidade {

    private final AlocacaoRepository alocacaoRepository;

    @Override
    public String codigo() {
        return "UMA_VAGA_POR_CELEBRACAO";
    }

    @Override
    public String descricao() {
        return "Candidato não pode ocupar mais de uma vaga na mesma celebração";
    }

    @Override
    public Map<String, Object> padroes() {
        return Map.of();
    }

    @Override
    public Optional<String> impedimento(Usuario candidato, ContextoElegibilidade contexto, Map<String, Object> parametros) {
        Long celebracaoId = contexto.celebracao().getId();
        for (Alocacao alocacao : alocacaoRepository.findByUsuarioIdAndActiveTrueAndStatusInAndIdNot(
                candidato.getId(), StatusConvite.OCUPANTES, contexto.alocacaoIgnoradaId())) {
            if (alocacao.getVaga().getCelebracao().getId().equals(celebracaoId)) {
                return Optional.of("Candidato já ocupa uma vaga nesta celebração");
            }
        }
        return Optional.empty();
    }
}
