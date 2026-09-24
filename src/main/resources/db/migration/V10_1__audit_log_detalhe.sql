-- ============================================================
-- V10.1: campo livre para guardar método+caminho nas auditorias de ação do
--        ADMIN numa paróquia assumida (Parte 2.2). audit_log ainda não tinha
--        nenhum gravador em uso; esta é a primeira migration que passa a
--        popular a tabela de fato.
-- ============================================================

ALTER TABLE audit_log ADD COLUMN detalhe VARCHAR(255);
