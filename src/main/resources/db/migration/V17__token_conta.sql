-- Sprint 2, cartão 1: token de conta (ativação, recuperação de senha e "Proteger minha conta").
-- O token puro só vai por e-mail; aqui fica o SHA-256 (token_hash), como no convite.
CREATE TABLE token_conta (
    id         BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuario (id),
    tipo       VARCHAR(12) NOT NULL CHECK (tipo IN ('ATIVACAO', 'RECUPERACAO', 'PROTECAO')),
    token_hash CHAR(64)    NOT NULL,
    expira_em  TIMESTAMP   NOT NULL,
    usado_em   TIMESTAMP,
    criado_em  TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_token_conta_hash ON token_conta (token_hash);
-- um token pendente por tipo e pessoa: pedir de novo mata o anterior
CREATE UNIQUE INDEX uq_token_conta_pendente ON token_conta (usuario_id, tipo) WHERE usado_em IS NULL;
