-- Cartão 2 (sprint 1): o lembrete gira o token sem matar o link do primeiro e-mail.
-- A alocação guarda os dois últimos tokens (atual em token_hash, anterior aqui).
ALTER TABLE alocacao
    ADD COLUMN token_hash_anterior CHAR(64);

CREATE UNIQUE INDEX uq_alocacao_token_anterior ON alocacao (token_hash_anterior) WHERE token_hash_anterior IS NOT NULL;
