-- ============================================================
-- V12: sorteio, escalação e convite por e-mail (Etapa 6, Parte 1).
--      alocacao ganha origem/escalado_por/respondido_em/justificativa/
--      substituida_em/token_hash; notificacao_enviada evita e-mail
--      duplicado (job reprocessando, reenvio, etc.).
-- ============================================================

ALTER TABLE alocacao
    ADD COLUMN origem          VARCHAR(20) NOT NULL DEFAULT 'COORDENADOR', -- SORTEIO | COORDENADOR
    ADD COLUMN escalado_por_id BIGINT REFERENCES usuario(id),
    ADD COLUMN respondido_em   TIMESTAMP,
    ADD COLUMN justificativa   VARCHAR(500),
    ADD COLUMN substituida_em  TIMESTAMP,
    ADD COLUMN token_hash      CHAR(64);

CREATE UNIQUE INDEX uq_alocacao_token ON alocacao (token_hash) WHERE token_hash IS NOT NULL;
CREATE INDEX idx_alocacao_prazo ON alocacao (data_limite_resposta) WHERE is_active AND status = 'PENDENTE';

-- evita e-mail/lembrete duplicado (job reprocessando, reenvio, etc.)
CREATE TABLE notificacao_enviada (
    id          BIGSERIAL PRIMARY KEY,
    paroquia_id BIGINT      NOT NULL REFERENCES paroquia(id),
    alocacao_id BIGINT      NOT NULL REFERENCES alocacao(id),
    tipo        VARCHAR(40) NOT NULL,
    enviada_em  TIMESTAMP   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_notificacao ON notificacao_enviada (alocacao_id, tipo);
