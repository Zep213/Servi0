-- Sprint 4, cartão 1: horário fixo da missa e quais horários cada pastoral cobre.
-- Só pode ser mergeada depois de V17 (token_conta), V18 (usuario_ativacao) e V19 (usuario_foto).

-- Horário fixo de missa de uma comunidade (padrão: domingo). dia_semana segue DayOfWeek: 1 = segunda ... 7 = domingo.
CREATE TABLE horario_missa (
    id            BIGSERIAL PRIMARY KEY,
    paroquia_id   BIGINT    NOT NULL REFERENCES paroquia (id),
    comunidade_id BIGINT    NOT NULL REFERENCES comunidade (id),
    dia_semana    SMALLINT  NOT NULL DEFAULT 7 CHECK (dia_semana BETWEEN 1 AND 7),
    hora          TIME      NOT NULL,
    is_active     BOOLEAN   NOT NULL DEFAULT TRUE,
    version       BIGINT    NOT NULL DEFAULT 0,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
CREATE UNIQUE INDEX uq_horario_missa ON horario_missa (comunidade_id, dia_semana, hora) WHERE is_active;
CREATE INDEX idx_horario_missa_paroquia ON horario_missa (paroquia_id);

-- Qual horário cada pastoral cobre (a Pascom cobre uma missa por domingo em cada comunidade).
CREATE TABLE cobertura_missa (
    id               BIGSERIAL PRIMARY KEY,
    paroquia_id      BIGINT    NOT NULL REFERENCES paroquia (id),
    pastoral_id      BIGINT    NOT NULL REFERENCES pastoral (id),
    horario_missa_id BIGINT    NOT NULL REFERENCES horario_missa (id),
    is_active        BOOLEAN   NOT NULL DEFAULT TRUE,
    version          BIGINT    NOT NULL DEFAULT 0,
    created_at       TIMESTAMP,
    updated_at       TIMESTAMP
);
CREATE UNIQUE INDEX uq_cobertura_missa ON cobertura_missa (pastoral_id, horario_missa_id) WHERE is_active;
CREATE INDEX idx_cobertura_missa_paroquia ON cobertura_missa (paroquia_id);

-- De qual horário fixo a celebração nasceu (NULL = criada à mão). O índice único NÃO filtra is_active de propósito:
-- a missa que o padre desativou (cancelou) não pode ser recriada pelo job de geração do cartão 2.
ALTER TABLE celebracao ADD COLUMN horario_missa_id BIGINT REFERENCES horario_missa (id);
CREATE UNIQUE INDEX uq_celebracao_horario_data ON celebracao (horario_missa_id, data) WHERE horario_missa_id IS NOT NULL;
