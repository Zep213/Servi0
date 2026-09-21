CREATE TABLE paroquia (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email_contato VARCHAR(255) NOT NULL,
    telefone VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    senha VARCHAR(255) NOT NULL,
    perfil VARCHAR(30) NOT NULL,
    paroquia_id BIGINT NOT NULL REFERENCES paroquia(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (email, paroquia_id)
);

CREATE TABLE pastoral (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    paroquia_id BIGINT NOT NULL REFERENCES paroquia(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE funcao (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    pastoral_id BIGINT NOT NULL REFERENCES pastoral(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE usuario_funcao (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    funcao_id BIGINT NOT NULL REFERENCES funcao(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (usuario_id, funcao_id)
);

CREATE TABLE comunidade (
    id BIGSERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    paroquia_id BIGINT NOT NULL REFERENCES paroquia(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE celebracao (
    id BIGSERIAL PRIMARY KEY,
    comunidade_id BIGINT NOT NULL REFERENCES comunidade(id),
    data DATE NOT NULL,
    hora TIME NOT NULL,
    tipo_data VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE vaga (
    id BIGSERIAL PRIMARY KEY,
    celebracao_id BIGINT NOT NULL REFERENCES celebracao(id),
    funcao_id BIGINT NOT NULL REFERENCES funcao(id),
    quantidade INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE alocacao (
    id BIGSERIAL PRIMARY KEY,
    vaga_id BIGINT NOT NULL REFERENCES vaga(id),
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    UNIQUE (vaga_id, usuario_id)
);

CREATE TABLE indisponibilidade (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id),
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    motivo VARCHAR(500),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE pedido_troca (
    id BIGSERIAL PRIMARY KEY,
    alocacao_id BIGINT NOT NULL REFERENCES alocacao(id),
    solicitante_id BIGINT NOT NULL REFERENCES usuario(id),
    destinatario_id BIGINT REFERENCES usuario(id),
    status VARCHAR(30) NOT NULL DEFAULT 'ABERTO',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE compromisso_agenda (
    id BIGSERIAL PRIMARY KEY,
    padre_id BIGINT NOT NULL REFERENCES usuario(id),
    titulo VARCHAR(255) NOT NULL,
    data DATE NOT NULL,
    hora TIME,
    tipo VARCHAR(100),
    comunidade_id BIGINT REFERENCES comunidade(id),
    privado BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    tipo_evento VARCHAR(100) NOT NULL,
    referencia_id BIGINT,
    usuario_id BIGINT REFERENCES usuario(id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Índices para as consultas mais comuns
CREATE INDEX idx_usuario_paroquia ON usuario(paroquia_id);
CREATE INDEX idx_celebracao_comunidade_data ON celebracao(comunidade_id, data);
CREATE INDEX idx_alocacao_usuario ON alocacao(usuario_id);
CREATE INDEX idx_pedido_troca_status ON pedido_troca(status);
