-- ============================================================
-- V6: papéis por pastoral (não globais da paróquia) e alterações
--     pendentes de aprovação feitas pelo vice.
-- ============================================================

CREATE TABLE usuario_pastoral (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    pastoral_id BIGINT NOT NULL REFERENCES pastoral(id),
    paroquia_id BIGINT NOT NULL REFERENCES paroquia(id),
    papel VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Um papel ativo por pessoa, por pastoral
CREATE UNIQUE INDEX uq_usuario_pastoral_ativo ON usuario_pastoral (usuario_id, pastoral_id) WHERE is_active;
CREATE INDEX idx_usuario_pastoral_pastoral ON usuario_pastoral (pastoral_id);
CREATE INDEX idx_usuario_pastoral_paroquia ON usuario_pastoral (paroquia_id);

CREATE TABLE alteracao_pendente (
    id BIGSERIAL PRIMARY KEY,
    alocacao_id BIGINT NOT NULL REFERENCES alocacao(id),
    pastoral_id BIGINT NOT NULL REFERENCES pastoral(id),
    paroquia_id BIGINT NOT NULL REFERENCES paroquia(id),
    autor_id BIGINT NOT NULL REFERENCES usuario(id),
    vaga_anterior_id BIGINT NOT NULL REFERENCES vaga(id),
    usuario_anterior_id BIGINT NOT NULL REFERENCES usuario(id),
    vaga_nova_id BIGINT NOT NULL REFERENCES vaga(id),
    usuario_novo_id BIGINT NOT NULL REFERENCES usuario(id),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX idx_alteracao_pendente_pastoral ON alteracao_pendente (pastoral_id, status);
