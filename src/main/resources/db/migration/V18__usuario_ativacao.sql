-- Sprint 2, cartão 2: conta criada sem senha, ativada pelo link do e-mail.
-- senha NULL = conta ainda não ativada (ou protegida, no cartão 4); ativado_em marca quando a pessoa definiu a senha.
ALTER TABLE usuario ALTER COLUMN senha DROP NOT NULL;
ALTER TABLE usuario ADD COLUMN ativado_em TIMESTAMP;

-- todas as contas que existem hoje já têm senha
UPDATE usuario SET ativado_em = now() WHERE senha IS NOT NULL;
