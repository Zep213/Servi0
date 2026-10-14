-- ============================================================
-- V13: cargos TECNICO e REDES_SOCIAIS em usuario_pastoral.
--     A coluna papel já é VARCHAR(20) sem CHECK: os valores novos
--     cabem sem mudar a tabela. Cada um é único por pastoral; o
--     índice garante isso mesmo com dois cadastros ao mesmo tempo.
-- ============================================================

CREATE UNIQUE INDEX uq_usuario_pastoral_cargo_unico
    ON usuario_pastoral (pastoral_id, papel)
    WHERE is_active AND papel IN ('TECNICO', 'REDES_SOCIAIS');
