-- ============================================================
-- V10: Perfil.COORDENADOR deixa de existir (Parte 2.1). Poder de gestão
--      dentro da pastoral agora vem só de PapelPastoral; o Perfil da conta
--      é só ADMIN, PADRE ou SERVIDOR.
-- ============================================================

UPDATE usuario SET perfil = 'SERVIDOR' WHERE perfil = 'COORDENADOR';
