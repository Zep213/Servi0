-- ============================================================
-- V7: financeiro por pastoral (Parte C, item 5). Cada pastoral tem seu
--     próprio caixa; acesso restrito a tesoureiro/coordenador daquela pastoral.
-- ============================================================

CREATE TABLE lancamento_financeiro (
    id BIGSERIAL PRIMARY KEY,
    pastoral_id BIGINT NOT NULL REFERENCES pastoral(id),
    paroquia_id BIGINT NOT NULL REFERENCES paroquia(id),
    tipo VARCHAR(10) NOT NULL,
    valor NUMERIC(12,2) NOT NULL,
    descricao VARCHAR(255) NOT NULL,
    data_lancamento DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_lancamento_financeiro_pastoral ON lancamento_financeiro (pastoral_id, is_active);
