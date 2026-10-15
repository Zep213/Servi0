-- ============================================================
-- V14: fila de e-mails (outbox) — Sprint 1, Cartão 1.
--      Antes, o envio dependia só de uma thread em memória: se o SMTP falhasse ou o app
--      reiniciasse, o e-mail se perdia. Agora toda saída passa por esta tabela, com
--      tentativas e backoff, e o EnvioEmailJob retoma o que não foi entregue.
-- ============================================================

CREATE TABLE email_saida (
    id                    BIGSERIAL PRIMARY KEY,
    paroquia_id           BIGINT REFERENCES paroquia(id),
    destinatario          VARCHAR(255) NOT NULL,
    assunto               VARCHAR(255) NOT NULL,
    template              VARCHAR(60),
    variaveis             JSONB,
    status                VARCHAR(10) NOT NULL DEFAULT 'PENDENTE'
        CHECK (status IN ('PENDENTE', 'ENVIADO', 'FALHOU')),
    tentativas            SMALLINT NOT NULL DEFAULT 0,
    proxima_tentativa_em  TIMESTAMP NOT NULL DEFAULT now(),
    ultimo_erro           VARCHAR(500),
    criado_em             TIMESTAMP NOT NULL DEFAULT now(),
    enviado_em            TIMESTAMP
);

CREATE INDEX idx_email_saida_pendente ON email_saida (proxima_tentativa_em) WHERE status = 'PENDENTE';
