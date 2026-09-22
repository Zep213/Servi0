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
- `PasswordEncoder` BCrypt (custo 12); a senha do usuário é gravada com hash.
- **`UsuarioPrincipal`** (`UserDetails` customizado): carrega `id`, `paroquiaId`, `nome`, `email` e `perfil` do usuário autenticado, evitando nova consulta ao banco a cada uso. Devolvido por `UsuarioDetailsService.loadUserByUsername`.
- **`UsuarioLogado`**: ponto único do sistema para descobrir quem está logado (`id()`, `paroquiaId()`), lendo do `SecurityContextHolder`. Services devem usar esta classe em vez de confiar em ids vindos do corpo da requisição.
- **`GET /api/me`** (`MeController` + `MeResponseDTO`): devolve id/nome/email/perfil/paróquia do usuário autenticado, sem tocar o repositório.
- **Bootstrap do primeiro ADMIN** (`BootstrapAdmin`, `ApplicationRunner`): quando a tabela `usuario` está vazia, cria a primeira `Paroquia` e o primeiro usuário `ADMIN` a partir de variáveis de ambiente (`SERVIO_ADMIN_EMAIL`, `SERVIO_ADMIN_SENHA` — mínimo 8 caracteres —, `SERVIO_ADMIN_NOME`, `SERVIO_PAROQUIA_NOME`). Sem essas variáveis definidas, o sistema fica inacessível (nenhum endpoint é público).
- **RBAC por perfil real** (`SecurityConfig`, `@EnableMethodSecurity`): cada rota exige o perfil certo (`hasRole`/`hasAnyRole`); regra padrão `anyRequest().denyAll()` — rota nova nasce bloqueada até ser liberada de propósito. Fecha a escalada de privilégio de rota (ver `erros-conhecidos.md`).

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

### Sessão, CSRF, rate limit e auditoria (etapa 3)
- **`V3__spring_session.sql`**: schema oficial do Spring Session para Postgres (`spring_session`, `spring_session_attributes`), criado pelo Flyway (`spring.session.jdbc.initialize-schema=never`).
- **Login por sessão** substitui o Basic Auth: `POST /api/auth/login` (form `email`/`senha`, processado pelo próprio Spring Security), `POST /api/auth/logout`. Sessões guardadas no Postgres via `spring-boot-starter-session-jdbc` (cookie `SERVIO_SESSION`, `httpOnly`, `secure` por ambiente via `COOKIE_SECURE`, `sameSite=lax`, timeout 7 dias, limpeza a cada 15 min).
  - O nome/flags do cookie são aplicados por um bean `DefaultCookieSerializerCustomizer` (não basta `server.servlet.session.cookie.*` — ver `erros-conhecidos.md`).
- **Proteção contra session fixation**: `sessionFixation(f -> f.changeSessionId())` — o id da sessão sempre muda no login, mesmo que o cliente já apresentasse uma sessão antes.
- **CSRF real para SPA** (`csrf.spa()`): cookie `XSRF-TOKEN` legível por JS + header `X-XSRF-TOKEN` obrigatório em toda escrita; `GET /api/auth/csrf` (`AuthController`) é a rota pública que o front chama para receber o cookie antes do login.
- **Rate limit de força bruta no login** (`TentativasLogin` + `LoginRateLimitFilter`): 5 falhas em 15 minutos, contadas por IP e por e-mail (cache Caffeine, expira sozinho); a 6ª tentativa recebe 429 com header `Retry-After`. Registrado direto na cadeia do Security (`addFilterBefore`), não como `@Component`, para não rodar duas vezes.
- **Resposta de login sempre igual em caso de falha** (`LoginHandlers`): e-mail inexistente e senha errada dão o mesmo 401 com a mesma mensagem — não revela quem tem conta.
- **`SessaoService.encerrarTodas(email)`**: derruba na hora todas as sessões de um usuário, indexadas pelo e-mail (`FindByIndexNameSessionRepository`). Acionado quando `UsuarioService.atualizar` muda perfil, e-mail ou senha, quando `desativar` um usuário, e pela própria troca de senha.
- **`POST /api/me/senha`** (`MeService` + `TrocaSenhaRequestDTO`): o usuário troca a própria senha (exige a senha atual); derruba todas as sessões, inclusive a atual — login de novo é obrigatório.
- **`AuditoriaLoginListener`**: grava `LOGIN_SUCESSO`/`LOGIN_FALHA` no `AuditLog` a partir dos eventos que o próprio Spring Security publica (`AuthenticationSuccessEvent`/`AbstractAuthenticationFailureEvent`); nunca grava a senha digitada.
- **Headers de segurança** em toda resposta: `Content-Security-Policy` (API: `default-src 'none'`), `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` (mais os padrões do Spring Security: `X-Content-Type-Options`, `Cache-Control` anti-cache).
- **Anti mass assignment**: `spring.jackson.deserialization.fail-on-unknown-properties=true` — campo a mais no JSON vira 400 em vez de ser silenciosamente ignorado.
- **Erros sem vazamento**: `server.error.include-stacktrace=never` e `include-message=never`; `GlobalHandleException` já cobria isso para as próprias respostas.
- **CI/CD** (`.github/workflows/ci.yml`): gitleaks (segredos commitados), `./mvnw -B verify` (build + testes unitários e de integração), build da imagem Docker, scan com Trivy (`CRITICAL,HIGH`, falha o build se achar). `.github/dependabot.yml` (maven, docker, github-actions, semanal).
- **GitHub → Code security**: secret scanning, push protection e Dependabot alerts ativados no repositório (gratuito, é público).
- **Testes de segurança** (`src/test/.../seguranca`, sufixo `*IT`, rodando via `maven-failsafe-plugin` com Postgres real em Testcontainers):
  - `AutorizacaoIT` — matriz de permissões por perfil (403 vs 401 vs rota bloqueada).
  - `LoginIT` — login válido, e-mail/senha errados respondem igual, rate limit, id de sessão muda no login, logout invalida o cookie.
  - `SessaoIT` — rebaixar o perfil de um usuário derruba a sessão dele.
  - `CsrfIT` — POST sem `X-XSRF-TOKEN` dá 403; com cookie+header, passa do CSRF.
  - `TenantIsolamentoIT` — ler, alterar, excluir e referenciar recurso de outra paróquia dá 404 (nunca 403).
  - `MassAssignmentIT` — campo que o DTO não declara vira 400.
  - `VazamentoIT` — nenhuma resposta traz senha/hash, erro genérico não expõe stack trace, headers de segurança presentes.
  - 23 testes no total, `reuseForks=false` no Surefire/Failsafe (cada classe `@SpringBootTest` isolada na própria JVM).
- **`Features/checklist-seguranca.md`**: checklist manual antes de ir para produção (HTTPS, X-Forwarded-For, segredos, porta do banco, backup, CI verde, logs, ZAP).

## Em desenvolvimento
- Ligar `AuditLogService` e `Notificador` às regras de negócio (login já audita; as demais ações ainda não).
- Fluxo de `PedidoTroca` (transições de status, só solicitante cancela, só destinatário aceita/recusa).
- Impedir que um COORDENADOR crie/promova alguém a `ADMIN` (ver `erros-conhecidos.md`, item 13).

Ver também: `features-futuras.md` e `erros-conhecidos.md`.
