-- ============================================================
-- V8: reuniões de pastoral (Parte C, item 3). Só o coordenador marca;
--     os demais papéis só podem solicitar (e-mail ao coordenador).
-- ============================================================

CREATE TABLE reuniao (
    id BIGSERIAL PRIMARY KEY,
    pastoral_id BIGINT NOT NULL REFERENCES pastoral(id),
    paroquia_id BIGINT NOT NULL REFERENCES paroquia(id),
    titulo VARCHAR(255) NOT NULL,
    data_hora TIMESTAMP NOT NULL,
    local VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_reuniao_pastoral ON reuniao (pastoral_id, is_active);
