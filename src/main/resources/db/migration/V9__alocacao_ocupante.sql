-- ============================================================
-- V9: recusada/expirada não ocupam mais o lugar na vaga (Parte 1.3). A
--     unicidade de vaga+usuário agora vale só entre quem de fato ocupa
--     (PENDENTE/ACEITA) — depois de recusar ou expirar, a pessoa pode ser
--     convidada de novo para a mesma vaga.
-- ============================================================

DROP INDEX uq_alocacao_ativa;

CREATE UNIQUE INDEX uq_alocacao_ocupante ON alocacao (vaga_id, usuario_id)
    WHERE is_active AND status IN ('PENDENTE', 'ACEITA');
