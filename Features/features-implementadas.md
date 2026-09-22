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

### Multi-tenant e integridade (etapa 2)
- **Migration `V2__multi_tenant_e_integridade.sql`**: adiciona `paroquia_id` às tabelas que ainda não tinham (`funcao`, `usuario_funcao`, `celebracao`, `vaga`, `alocacao`, `indisponibilidade`, `pedido_troca`, `compromisso_agenda`, `audit_log` opcional), populando os registros existentes; adiciona `version` (optimistic locking) em todas as entidades tenant + `Paroquia`; troca as `UNIQUE` constraints antigas por índices únicos parciais `WHERE is_active` (e-mail de usuário único entre ativos no sistema todo, usuário-função, alocação).
- **`TenantEntity`** (`MappedSuperclass`, estende `ActivatableEntity`): campo `paroquia_id` controlado pelo servidor, nunca aceito do cliente (`updatable = false`).
- **`TenantRepository<E>`**: base para os 11 repositories de entidades tenant, com `findByIdAndParoquiaIdAndActiveTrue` e `findByParoquiaIdAndActiveTrue(Pageable)` — toda consulta já nasce restrita à paróquia.
- **`CrudService` reescrito**: `listar(Pageable)` retorna `Page<Res>` filtrado pela paróquia do usuário logado (`UsuarioLogado`, injetado por setter); `criar()` seta `paroquiaId` automaticamente; `validar(entidade)` roda no criar **e** no atualizar; `referencia()`/`referenciaOpcional()` só resolvem entidades da mesma paróquia (404 se forem de outra).
- **Identidade sempre do usuário autenticado, nunca do JSON**: `paroquiaId` sumiu de todos os DTOs de request; `CompromissoAgendaService` usa `usuarioId()` como padre; `PedidoTrocaService` usa `usuarioId()` como solicitante; `IndisponibilidadeService` só deixa marcar indisponibilidade de outro usuário se o logado for ADMIN/COORDENADOR (senão 403).
- **`UsuarioUpdateDTO`**: senha opcional na atualização (mantém a atual se vier em branco); `UsuarioRequestDTO`/`UsuarioUpdateDTO` limitam a senha a 72 caracteres (limite real do BCrypt).
- **`ParoquiaService`/`ParoquiaController` viram "minha paróquia"**: só `GET`/`PUT /api/paroquias/minha` (resolvidos pelo `paroquiaId` do usuário logado); não há mais listar, buscar por id, criar ou desativar paróquia pela API.
- **Paginação em todos os 11 controllers tenant**: `GET /api/<recurso>?page=&size=&sort=`, com `@PageableDefault` e sort por campo relevante da entidade (`nome`, `data`, `dataInicio` ou `id`).
- **Concorrência otimista**: `GlobalHandleException` trata `ObjectOptimisticLockingFailureException` como 409 com mensagem amigável.
- **`@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`** em `ServioApplication`, para serializar `Page<T>` de forma estável entre versões do Spring.
- Testado ponta a ponta via `curl` com banco zerado: listagem paginada, criação de usuário sem `paroquiaId` no corpo (herdado do usuário logado), `GET /api/paroquias/minha`, e desativar+recriar usuário com o mesmo e-mail (antes dava 409, agora funciona).

## Em desenvolvimento
- Aplicar a Basic Auth no checkout principal e no container.
- Restringir rotas por perfil com `@PreAuthorize` (infraestrutura pronta; falta sobretudo impedir que qualquer usuário logado crie um `ADMIN`).
- Ligar `AuditLogService` e `Notificador` às regras de negócio.
- Fluxo de `PedidoTroca` (transições de status, só solicitante cancela, só destinatário aceita/recusa).

Ver também: `features-futuras.md` e `erros-conhecidos.md`.
