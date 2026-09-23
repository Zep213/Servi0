package br.com.zep.servio.model.enumerated;

import java.util.Set;

public enum StatusConvite {
    PENDENTE,
    ACEITA,
    RECUSADA,
    EXPIRADA;

    /** Status que ocupam de fato a vaga: RECUSADA e EXPIRADA liberam o lugar. */
    public static final Set<StatusConvite> OCUPANTES = Set.of(PENDENTE, ACEITA);
}
