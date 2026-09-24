package br.com.zep.servio.model.enumerated;

/**
 * Tipo de celebração que um modelo de vaga cobre. TODOS é exclusivo do modelo
 * (Celebracao.tipo nunca é TODOS): cobre missa dominical e evento igualmente.
 */
public enum TipoCelebracaoModelo {
    MISSA_DOMINICAL,
    EVENTO,
    TODOS
}
