package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.model.Alocacao;
import br.com.zep.servio.model.Usuario;
import br.com.zep.servio.model.UsuarioPastoral;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.StatusConvite;
import br.com.zep.servio.repository.AlocacaoRepository;
import br.com.zep.servio.repository.UsuarioPastoralRepository;
import br.com.zep.servio.service.escalacao.regra.ContextoElegibilidade;
import br.com.zep.servio.service.escalacao.regra.RegraElegibilidade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Aplica o catálogo de regras, já resolvido com a configuração da pastoral, a um candidato/vaga. */
@Service
@RequiredArgsConstructor
public class ElegibilidadeService {

    private final ConfiguracaoPastoralService configuracaoPastoralService;
    private final UsuarioPastoralRepository usuarioPastoralRepository;
    private final AlocacaoRepository alocacaoRepository;
    private final Clock clock;

    /**
     * Motivos de impedimento do candidato para a vaga; lista vazia = candidato elegível.
     * alocacaoIgnoradaId: a própria alocação sendo editada (0 numa criação), para as
     * regras não considerarem o registro que está sendo validado como um conflito consigo mesmo.
     */
    public List<String> impedimentos(Usuario candidato, Vaga vaga, Long alocacaoIgnoradaId) {
        Long pastoralId = vaga.getFuncao().getPastoral().getId();
        ContextoElegibilidade contexto = new ContextoElegibilidade(vaga, alocacaoIgnoradaId);

        List<String> impedimentos = new ArrayList<>();
        for (RegraElegibilidade regra : configuracaoPastoralService.catalogo()) {
            if (!configuracaoPastoralService.ativa(pastoralId, regra.codigo())) {
                continue;
            }
            Map<String, Object> parametros = configuracaoPastoralService.parametros(pastoralId, regra);
            regra.impedimento(candidato, contexto, parametros).ifPresent(impedimentos::add);
        }
        return impedimentos;
    }

    /**
     * Todos os membros ativos da pastoral dona da função da vaga, cada um com os motivos de
     * impedimento e a última vez que serviu. Membros e últimos serviços vêm em uma consulta
     * cada. As regras (impedimentos) ainda consultam por candidato: aceitável no tamanho de
     * uma pastoral. Dívida conhecida: se a pastoral crescer, essas regras devem receber os
     * dados já carregados em lote.
     */
    public List<CandidatoAvaliado> avaliar(Vaga vaga) {
        Long pastoralId = vaga.getFuncao().getPastoral().getId();
        Long paroquiaId = vaga.getParoquiaId();

        List<Usuario> membros = usuarioPastoralRepository.findMembrosAtivosComUsuario(pastoralId, paroquiaId)
                .stream().map(UsuarioPastoral::getUsuario).toList();
        if (membros.isEmpty()) {
            return List.of();
        }

        Map<Long, StatusConvite> statusNaVaga = new HashMap<>();
        for (Alocacao alocacao : alocacaoRepository.findByVagaIdAndActiveTrue(vaga.getId())) {
            statusNaVaga.put(alocacao.getUsuario().getId(), alocacao.getStatus());
        }

        List<Long> ids = membros.stream().map(Usuario::getId).toList();
        Map<Long, LocalDate> ultimos = new HashMap<>();
        for (AlocacaoRepository.UltimoServico ultimo : alocacaoRepository.ultimosServicos(
                paroquiaId, StatusConvite.ACEITA, ids, LocalDate.now(clock))) {
            ultimos.put(ultimo.getUsuarioId(), ultimo.getUltimo());
        }

        return membros.stream()
                .map(membro -> new CandidatoAvaliado(membro, motivosDaVaga(membro, vaga, statusNaVaga),
                        ultimos.get(membro.getId())))
                .toList();
    }

    /** Quem já ocupa esta vaga aparece como impedido (a escalação manual já barra com 409). */
    private List<String> motivosDaVaga(Usuario membro, Vaga vaga, Map<Long, StatusConvite> statusNaVaga) {
        List<String> motivos = new ArrayList<>();
        StatusConvite status = statusNaVaga.get(membro.getId());
        if (status != null && StatusConvite.OCUPANTES.contains(status)) {
            motivos.add("Já está escalado nesta vaga");
        }
        motivos.addAll(impedimentos(membro, vaga, 0L));
        return motivos;
    }
}
