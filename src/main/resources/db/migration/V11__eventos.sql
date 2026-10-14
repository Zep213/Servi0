-- ============================================================
-- V11: modelo de eventos (Parte 3). Celebração ganha tipo/título;
--      vaga pode nascer sem quantidade ("responsabilizar a pastoral");
--      modelo_vaga permite cobertura automática por pastoral.
-- ============================================================

ALTER TABLE celebracao
    ADD COLUMN tipo   VARCHAR(20) NOT NULL DEFAULT 'MISSA_DOMINICAL',
    ADD COLUMN titulo VARCHAR(120);

ALTER TABLE vaga ALTER COLUMN quantidade DROP NOT NULL;
ALTER TABLE vaga
    ADD COLUMN horario_chegada TIME,
    ADD COLUMN observacao      VARCHAR(500);

CREATE TABLE modelo_vaga (
    id              BIGSERIAL PRIMARY KEY,
    pastoral_id     BIGINT      NOT NULL REFERENCES pastoral(id),
    paroquia_id     BIGINT      NOT NULL REFERENCES paroquia(id),
    funcao_id       BIGINT      NOT NULL REFERENCES funcao(id),
    tipo_celebracao VARCHAR(20) NOT NULL,
    quantidade      INT         NOT NULL CHECK (quantidade > 0),
    is_active       BOOLEAN     NOT NULL DEFAULT TRUE,
    version         BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);
CREATE UNIQUE INDEX uq_modelo_vaga ON modelo_vaga (pastoral_id, funcao_id, tipo_celebracao) WHERE is_active;
CREATE INDEX idx_modelo_vaga_paroquia ON modelo_vaga (paroquia_id);
