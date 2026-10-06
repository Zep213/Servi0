package br.com.zep.servio.service.escalacao;

import br.com.zep.servio.model.Usuario;

import java.time.LocalDate;
import java.util.List;

/**
 * Um membro da pastoral avaliado para uma vaga. motivos vazio = elegível.
 * ultimaVezQueServiu é nulo para quem nunca serviu (o sorteio põe essas pessoas primeiro).
 */
public record CandidatoAvaliado(Usuario usuario, List<String> motivos, LocalDate ultimaVezQueServiu) {

    public boolean elegivel() {
        return motivos.isEmpty();
    }
}
