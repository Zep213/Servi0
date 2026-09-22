# Features implementadas / em desenvolvimento

Estado do projeto em 2026-09-21.

## Implementado

### Infraestrutura
- Spring Boot com Java 25, Maven, JPA/Hibernate, Lombok e MapStruct.
- PostgreSQL 17 via Docker Compose (`servio-db`), porta 5432 exposta só em `127.0.0.1`.
- `Dockerfile` multi-stage e serviço `app` no `docker-compose.yml` (porta 8080).
- Configuração por variáveis de ambiente (`DB_URL`, `DB_USER`, `DB_PASSWORD`), com padrões `servio`/`servio`; modelo em `.env.example`.
- Migrations com Flyway: `V1__init` cria as 13 tabelas do domínio. `ddl-auto=validate` confere o schema.

### Modelo de dados
Paroquia, Comunidade, Pastoral, Funcao, Usuario, UsuarioFuncao, Celebracao, Vaga, Alocacao, Indisponibilidade, PedidoTroca, CompromissoAgenda e AuditLog.
- Exclusão lógica (`is_active`) em todas, exceto `AuditLog`.
- Enums: `Perfil` (ADMIN, COORDENADOR, PADRE, SERVIDOR), `StatusTroca` (ABERTO, ACEITO, RECUSADO, CANCELADO) e `TipoData` (NORMAL, SOLENIDADE, FESTA, FERIADO).

### API REST
- Controllers, services, mappers e DTOs de request/response para os 13 recursos, sob `/api/<recurso>`.
- `CrudService` genérico: listar, buscar, criar, atualizar e excluir (lógico).
- Tratamento global de erros (`GlobalHandleException`) com `ProblemDetail`: 400 (validação), 409 (conflito/integridade), 404 (recurso não encontrado) e 500.
- Regras de unicidade: e-mail de usuário por paróquia; usuário já alocado na mesma vaga.

### Segurança
- `PasswordEncoder` BCrypt; a senha do usuário é gravada com hash.
- **Basic Auth com a tabela `usuario`** (`UsuarioDetailsService` + `SecurityFilterChain`): login por e-mail, só usuários ativos, role derivada do `Perfil`, sessão stateless, CSRF desligado. Testado: login válido 200, inválido 401.
  - Situação: commitado na branch `worktree-basic-auth` (`6f5ae18`); **falta copiar para o checkout principal e rebuildar o container**.

### Suporte
- `AuditLogService.registrar(...)` e endpoints de leitura de auditoria (a gravação ainda não é acionada por nenhum service; ver `erros-conhecidos.md`).
- Interface `Notificador` e `EmailNotificador` (não envia se `spring.mail.host` não estiver configurado).

## Em desenvolvimento
- Aplicar a Basic Auth no checkout principal e no container.
- Criar o primeiro usuário ADMIN (bootstrap).
- Ligar `AuditLogService` e `Notificador` às regras de negócio.

Ver também: `features-futuras.md` e `erros-conhecidos.md`.
