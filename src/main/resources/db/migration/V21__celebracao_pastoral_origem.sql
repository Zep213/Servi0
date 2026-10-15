-- Sprint 4, cartão 3: quem criou o evento. A coordenação cria evento da própria pastoral; o padre cria os demais.
-- NULL = criada pelo padre/ADMIN (ou missa gerada pelo horário fixo). Só pode ser mergeada depois da V20.
ALTER TABLE celebracao ADD COLUMN pastoral_origem_id BIGINT REFERENCES pastoral (id);
CREATE INDEX idx_celebracao_pastoral_origem ON celebracao (pastoral_origem_id) WHERE pastoral_origem_id IS NOT NULL;
