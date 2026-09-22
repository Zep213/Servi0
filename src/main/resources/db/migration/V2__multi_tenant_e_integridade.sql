-- ============================================================
-- V2: paroquia_id em todas as tabelas do tenant,
--     unicidade só entre ativos e controle de concorrência.
-- ============================================================

-- 1. Coluna paroquia_id
ALTER TABLE funcao             ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);
ALTER TABLE usuario_funcao     ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);
ALTER TABLE celebracao         ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);
ALTER TABLE vaga               ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);
ALTER TABLE alocacao           ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);
ALTER TABLE indisponibilidade  ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);
ALTER TABLE pedido_troca       ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);
ALTER TABLE compromisso_agenda ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);
ALTER TABLE audit_log          ADD COLUMN paroquia_id BIGINT REFERENCES paroquia(id);

-- 2. Preenche dados já existentes (celebracao antes de vaga: vaga depende dela)
UPDATE funcao f             SET paroquia_id = p.paroquia_id  FROM pastoral p    WHERE f.pastoral_id = p.id;
UPDATE usuario_funcao uf    SET paroquia_id = u.paroquia_id  FROM usuario u     WHERE uf.usuario_id = u.id;
UPDATE celebracao c         SET paroquia_id = co.paroquia_id FROM comunidade co WHERE c.comunidade_id = co.id;
UPDATE vaga v               SET paroquia_id = c.paroquia_id  FROM celebracao c  WHERE v.celebracao_id = c.id;
UPDATE alocacao a           SET paroquia_id = u.paroquia_id  FROM usuario u     WHERE a.usuario_id = u.id;
UPDATE indisponibilidade i  SET paroquia_id = u.paroquia_id  FROM usuario u     WHERE i.usuario_id = u.id;
UPDATE pedido_troca t       SET paroquia_id = u.paroquia_id  FROM usuario u     WHERE t.solicitante_id = u.id;
UPDATE compromisso_agenda a SET paroquia_id = u.paroquia_id  FROM usuario u     WHERE a.padre_id = u.id;
UPDATE audit_log l          SET paroquia_id = u.paroquia_id  FROM usuario u     WHERE l.usuario_id = u.id;

-- audit_log fica opcional (registros de sistema podem não ter usuário)
ALTER TABLE funcao             ALTER COLUMN paroquia_id SET NOT NULL;
ALTER TABLE usuario_funcao     ALTER COLUMN paroquia_id SET NOT NULL;
ALTER TABLE celebracao         ALTER COLUMN paroquia_id SET NOT NULL;
ALTER TABLE vaga               ALTER COLUMN paroquia_id SET NOT NULL;
ALTER TABLE alocacao           ALTER COLUMN paroquia_id SET NOT NULL;
ALTER TABLE indisponibilidade  ALTER COLUMN paroquia_id SET NOT NULL;
ALTER TABLE pedido_troca       ALTER COLUMN paroquia_id SET NOT NULL;
ALTER TABLE compromisso_agenda ALTER COLUMN paroquia_id SET NOT NULL;

-- 3. Unicidade só entre registros ATIVOS
ALTER TABLE usuario        DROP CONSTRAINT usuario_email_paroquia_id_key;
ALTER TABLE usuario_funcao DROP CONSTRAINT usuario_funcao_usuario_id_funcao_id_key;
ALTER TABLE alocacao       DROP CONSTRAINT alocacao_vaga_id_usuario_id_key;

-- e-mail único no sistema todo: resolve também a ambiguidade no login
CREATE UNIQUE INDEX uq_usuario_email_ativo  ON usuario (lower(email))                WHERE is_active;
CREATE UNIQUE INDEX uq_usuario_funcao_ativo ON usuario_funcao (usuario_id, funcao_id) WHERE is_active;
CREATE UNIQUE INDEX uq_alocacao_ativa       ON alocacao (vaga_id, usuario_id)         WHERE is_active;

-- 4. Optimistic locking
ALTER TABLE paroquia           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usuario            ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pastoral           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE funcao             ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE usuario_funcao     ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE comunidade         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE celebracao         ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vaga               ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE alocacao           ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE indisponibilidade  ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE pedido_troca       ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE compromisso_agenda ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- 5. Índices dos filtros por paróquia
-- idx_usuario_paroquia já existe desde a V1
CREATE INDEX idx_celebracao_paroquia  ON celebracao (paroquia_id, data);
CREATE INDEX idx_vaga_paroquia        ON vaga (paroquia_id);
CREATE INDEX idx_alocacao_paroquia    ON alocacao (paroquia_id);
CREATE INDEX idx_troca_paroquia       ON pedido_troca (paroquia_id);
CREATE INDEX idx_compromisso_paroquia ON compromisso_agenda (paroquia_id, data);
