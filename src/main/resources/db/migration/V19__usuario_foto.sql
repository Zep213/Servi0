-- Sprint 2, cartão 6: foto de perfil opcional. Guardada no banco (hospedagem ainda não decidida): a imagem
-- final é um JPEG de no máximo 512 x 512, poucas dezenas de KB. A paróquia vem do usuário dono.
CREATE TABLE usuario_foto (
    usuario_id    BIGINT       PRIMARY KEY REFERENCES usuario (id),
    conteudo      BYTEA        NOT NULL,
    tipo_conteudo VARCHAR(20)  NOT NULL CHECK (tipo_conteudo = 'image/jpeg'),
    atualizado_em TIMESTAMP    NOT NULL DEFAULT now()
);
