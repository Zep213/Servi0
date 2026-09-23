-- ============================================================
-- V5: status de convite e prazo de resposta na alocação.
--     O prazo em si não é gravado aqui: vem de pastoral_config (PRAZO_RESPOSTA).
-- ============================================================

ALTER TABLE alocacao ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE';
ALTER TABLE alocacao ADD COLUMN data_limite_resposta TIMESTAMP;

CREATE INDEX idx_alocacao_status ON alocacao (status);
