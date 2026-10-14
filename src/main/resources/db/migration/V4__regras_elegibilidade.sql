-- ============================================================
-- V4: pastoral_config — parâmetros de elegibilidade e de convite
--     configuráveis por pastoral, sem nada fixo no código.
-- ============================================================

CREATE TABLE pastoral_config (
    id BIGSERIAL PRIMARY KEY,
    pastoral_id BIGINT NOT NULL REFERENCES pastoral(id),
    paroquia_id BIGINT NOT NULL REFERENCES paroquia(id),
    chave VARCHAR(50) NOT NULL,
    ativa BOOLEAN NOT NULL DEFAULT TRUE,
    parametros JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Uma configuração ativa por chave, por pastoral
CREATE UNIQUE INDEX uq_pastoral_config_ativo ON pastoral_config (pastoral_id, chave) WHERE is_active;
CREATE INDEX idx_pastoral_config_paroquia ON pastoral_config (paroquia_id);
