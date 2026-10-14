package br.com.zep.servio.model.enumerated;

import java.util.Set;

public enum StatusConvite {
    PENDENTE,
    ACEITA,
    RECUSADA,
    EXPIRADA,
    SUBSTITUIDA;

    /** Status que ocupam de fato a vaga: os demais liberam o lugar. */
    public static final Set<StatusConvite> OCUPANTES = Set.of(PENDENTE, ACEITA);
}
