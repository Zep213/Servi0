package br.com.zep.servio.service;

import br.com.zep.servio.model.Celebracao;
import br.com.zep.servio.model.ModeloVaga;
import br.com.zep.servio.model.Pastoral;
import br.com.zep.servio.model.Vaga;
import br.com.zep.servio.model.enumerated.TipoCelebracaoModelo;
import br.com.zep.servio.repository.ModeloVagaRepository;
import br.com.zep.servio.repository.PastoralRepository;
import br.com.zep.servio.repository.VagaRepository;
import br.com.zep.servio.service.escalacao.ConfiguracaoPastoralService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Único ponto que cria vagas a partir de um modelo (Parte 3.3): usado tanto ao nascer uma
 * celebração nova (cobertura automática ligada) quanto em "aplicar-futuras". Nunca duplica
 * vaga da mesma função numa celebração que já tem uma.
 */
@Service
@RequiredArgsConstructor
public class CoberturaAutomaticaService {

    private final PastoralRepository pastoralRepository;
    private final ModeloVagaRepository modeloVagaRepository;
    private final VagaRepository vagaRepository;
    private final ConfiguracaoPastoralService configuracaoPastoralService;

    @Transactional
    public void aplicarNaCriacao(Celebracao celebracao, Long paroquiaId) {
        for (Pastoral pastoral : pastoralRepository.findByParoquiaIdAndActiveTrue(paroquiaId)) {
            if (!configuracaoPastoralService.coberturaAutomaticaLigada(pastoral.getId())) {
                continue;
            }
            for (ModeloVaga modelo : modeloVagaRepository.findByPastoralIdAndActiveTrue(pastoral.getId())) {
                aplicarModelo(modelo, celebracao);
            }
        }
    }

    @Transactional
    public int aplicarFuturas(Pastoral pastoral, List<Celebracao> celebracoesFuturas) {
        List<ModeloVaga> modelos = modeloVagaRepository.findByPastoralIdAndActiveTrue(pastoral.getId());
        int criadas = 0;
        for (Celebracao celebracao : celebracoesFuturas) {
            for (ModeloVaga modelo : modelos) {
                if (aplicarModelo(modelo, celebracao)) {
                    criadas++;
                }
            }
        }
        return criadas;
    }

    private boolean aplicarModelo(ModeloVaga modelo, Celebracao celebracao) {
        if (!aplicavel(modelo, celebracao)) {
            return false;
        }
        if (vagaRepository.existsByCelebracaoIdAndFuncaoIdAndActiveTrue(celebracao.getId(), modelo.getFuncao().getId())) {
            return false;
        }
        Vaga vaga = new Vaga();
        vaga.setCelebracao(celebracao);
        vaga.setFuncao(modelo.getFuncao());
        vaga.setQuantidade(modelo.getQuantidade());
        vaga.setParoquiaId(celebracao.getParoquiaId());
        vagaRepository.save(vaga);
        return true;
    }

    private boolean aplicavel(ModeloVaga modelo, Celebracao celebracao) {
        return modelo.getTipoCelebracao() == TipoCelebracaoModelo.TODOS
                || modelo.getTipoCelebracao().name().equals(celebracao.getTipo().name());
    }
}
