# Features implementadas / em desenvolvimento

Estado do projeto em 2026-09-22.

## Implementado

### Infraestrutura
- Spring Boot com Java 25, Maven, JPA/Hibernate, Lombok e MapStruct.
- PostgreSQL 17 via Docker Compose (`servio-db`), porta 5432 exposta só em `127.0.0.1`.
- `Dockerfile` multi-stage e serviço `app` no `docker-compose.yml` (porta 8080).
- Configuração por variáveis de ambiente (`DB_URL`, `DB_USER`, `DB_PASSWORD`), com padrões `servio`/`servio`; `.env` local (não versionado).
- `.env` local lido automaticamente em dev via `springboot4-dotenv` (funciona igual rodando pela IDE/`mvn spring-boot:run` ou via `docker compose`; variáveis de ambiente reais sempre têm prioridade sobre o `.env`).
- Migrations com Flyway: `V1__init` cria as 13 tabelas do domínio. `ddl-auto=validate` confere o schema.

### Modelo de dados
Paroquia, Comunidade, Pastoral, Funcao, Usuario, UsuarioFuncao, Celebracao, Vaga, Alocacao, Indisponibilidade, PedidoTroca, CompromissoAgenda e AuditLog.
- Exclusão lógica (`is_active`) em todas, exceto `AuditLog`.
- Enums: `Perfil` (ADMIN, COORDENADOR, PADRE, SERVIDOR), `StatusTroca` (ABERTO, ACEITO, RECUSADO, CANCELADO) e `TipoData` (NORMAL, SOLENIDADE, FESTA, FERIADO).

### API REST
- Controllers, services, mappers e DTOs de request/response para os 13 recursos, sob `/api/<recurso>`.
- `CrudService` genérico: listar, buscar, criar, atualizar e excluir (lógico).
- Tratamento global de erros (`GlobalHandleException`) com `ProblemDetail`: 400 (validação e JSON inválido), 403 (acesso negado), 404 (rota/recurso não encontrado), 405 (método não suportado), 409 (conflito/integridade) e 500.
- Regras de unicidade: e-mail de usuário por paróquia; usuário já alocado na mesma vaga.

### Segurança
- `PasswordEncoder` BCrypt; a senha do usuário é gravada com hash.
- **Basic Auth com a tabela `usuario`** (`UsuarioDetailsService` + `SecurityFilterChain`): login por e-mail, só usuários ativos, role derivada do `Perfil`. Aplicado na `main`.
  - Provisório: será substituído por login com sessão (Spring Session JDBC + cookie `HttpOnly` + CSRF) na etapa 3.
- **`UsuarioPrincipal`** (`UserDetails` customizado): carrega `id`, `paroquiaId`, `nome`, `email` e `perfil` do usuário autenticado, evitando nova consulta ao banco a cada uso. Devolvido por `UsuarioDetailsService.loadUserByUsername`.
- **`UsuarioLogado`**: ponto único do sistema para descobrir quem está logado (`id()`, `paroquiaId()`), lendo do `SecurityContextHolder`. Services devem usar esta classe em vez de confiar em ids vindos do corpo da requisição (ver `erros-conhecidos.md`, item 14).
- **`GET /api/me`** (`MeController` + `MeResponseDTO`): devolve id/nome/email/perfil/paróquia do usuário autenticado, sem tocar o repositório.
- **Bootstrap do primeiro ADMIN** (`BootstrapAdmin`, `ApplicationRunner`): quando a tabela `usuario` está vazia, cria a primeira `Paroquia` e o primeiro usuário `ADMIN` a partir de variáveis de ambiente (`SERVIO_ADMIN_EMAIL`, `SERVIO_ADMIN_SENHA` — mínimo 8 caracteres —, `SERVIO_ADMIN_NOME`, `SERVIO_PAROQUIA_NOME`). Sem essas variáveis definidas, o sistema fica inacessível (nenhum endpoint é público).
- **`@EnableMethodSecurity`** habilitado em `SecurityConfig` — infraestrutura pronta para `@PreAuthorize` nos services/controllers. Ainda não aplicado em nenhum endpoint (ver `erros-conhecidos.md`, item 13).

### Suporte
- `AuditLogService.registrar(...)` e endpoints de leitura de auditoria (a gravação ainda não é acionada por nenhum service; ver `erros-conhecidos.md`).
- Interface `Notificador` e `EmailNotificador` (não envia se `spring.mail.host` não estiver configurado).

## Em desenvolvimento
- Aplicar a Basic Auth no checkout principal e no container.
- Restringir rotas por perfil com `@PreAuthorize` (infraestrutura pronta, regras ainda não escritas).
- Ligar `AuditLogService` e `Notificador` às regras de negócio.

Ver também: `features-futuras.md` e `erros-conhecidos.md`.
