package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Sorteio justo de vagas. Embaralha os elegíveis e depois ordena, de forma estável, por quem
 * está há mais tempo sem servir (quem nunca serviu vem primeiro). O embaralhamento só decide
 * os empates. O gerador é injetado: em produção é o SecureRandom, nos testes uma semente fixa.
 */
@Service
@RequiredArgsConstructor
public class SorteioService {

    private final RandomGenerator random;

    public List<Usuario> escolher(List<CandidatoAvaliado> candidatos, int quantidade) {
        if (quantidade <= 0) {
            return List.of();
        }
        List<CandidatoAvaliado> embaralhados = new ArrayList<>(candidatos.stream()
                .filter(CandidatoAvaliado::elegivel)
                .toList());
        // Fisher-Yates com o gerador injetado (Collections.shuffle exigiria java.util.Random).
        for (int i = embaralhados.size() - 1; i > 0; i--) {
            Collections.swap(embaralhados, i, random.nextInt(i + 1));
        }
        embaralhados.sort(Comparator.comparing(CandidatoAvaliado::ultimaVezQueServiu,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        return embaralhados.stream()
                .limit(quantidade)
                .map(CandidatoAvaliado::usuario)
                .toList();
    }
}
