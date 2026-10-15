-- Telefone (login alternativo e contato) e preferência de aparência na conta do usuário.
-- telefone guardado normalizado: +55 + DDD + número (10 ou 11 dígitos).
ALTER TABLE usuario ADD COLUMN telefone       VARCHAR(20);
ALTER TABLE usuario ADD COLUMN aparencia      VARCHAR(12) NOT NULL DEFAULT 'AUTOMATICO';
ALTER TABLE usuario ADD COLUMN telefone_pulos SMALLINT    NOT NULL DEFAULT 0;

ALTER TABLE usuario ADD CONSTRAINT ck_usuario_aparencia
    CHECK (aparencia IN ('AUTOMATICO', 'CLARO', 'ESCURO'));
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_telefone
    CHECK (telefone IS NULL OR telefone ~ '^\+55[1-9][0-9]{9,10}$');

CREATE UNIQUE INDEX uq_usuario_telefone_ativo ON usuario (telefone)
    WHERE is_active AND telefone IS NOT NULL;
