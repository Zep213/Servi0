package br.com.zep.servio.model.enumerated;

public enum PapelPastoral {
    COORDENADOR,
    VICE,
    SECRETARIO,
    TESOUREIRO,
    /** Técnico de TI: mesmo acesso do VICE. No máximo um por pastoral. */
    TECNICO,
    /** Quem posta nas redes sociais da paróquia: acesso de MEMBRO. No máximo um por pastoral. */
    REDES_SOCIAIS,
    MEMBRO
}
