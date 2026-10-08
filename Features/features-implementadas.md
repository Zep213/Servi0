# Features implementadas / em desenvolvimento

Estado do projeto em 2026-10-07.

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

### Escalação, convite e e-mail (etapa 6)
- **Migration `V12__sorteio_convite.sql`**: `alocacao` ganha `origem` (SORTEIO/COORDENADOR), `escalado_por_id`, `respondido_em`, `justificativa`, `substituida_em` e `token_hash`; nova tabela `notificacao_enviada` com índice único `(alocacao_id, tipo)` para não mandar o mesmo e-mail duas vezes.
- **Relógio único** (`ClockConfig`, `servio.fuso`, padrão `America/Fortaleza`): todo "agora" de escalação, convite e jobs passa pelo `Clock`; testes usam relógio fixo.
- **Motor de escalação** (`service/escalacao/`):
  - `ElegibilidadeService.avaliar(vaga)` lista os membros da pastoral dona da função, com motivos de impedimento e data da última vez que serviram.
  - `SorteioService` embaralha os elegíveis e ordena por quem está há mais tempo sem servir (quem nunca serviu primeiro); o embaralhamento decide os empates. Usa `RandomGenerator` trocável por semente nos testes.
  - `EscalacaoService` (cada operação numa transação): sortear uma celebração de missa dominical ou uma vaga; listar candidatos; escalar (com `forcar` só para COORDENADOR, PADRE ou ADMIN); substituir; reenviar convite (no máximo 1 por hora).
  - Vaga sem gente suficiente fica aberta e o coordenador é avisado; a regra nunca é relaxada sozinha.
  - Concorrência: a vaga é travada com `PESSIMISTIC_WRITE` antes de contar os ocupantes, então duas operações simultâneas nunca passam da quantidade.
- **Convite pelo link do e-mail** (Parte 3):
  - Token de 32 bytes (`SecureRandom`, Base64 URL-safe); no banco fica só o SHA-256. O token puro só existe no evento em memória e nunca vai para log.
  - `POST /api/confirmacoes/detalhes` e `POST /api/confirmacoes/responder`, públicas, com rate limit de 30 por minuto por IP. Token inválido, reutilizado ou de alocação inativa recebe a mesma 404 genérica. Prazo vencido grava EXPIRADA e responde 410.
  - Link do e-mail: `{servio.front-url}/convite#{token}`; o fragmento não vai para logs nem para o Referer.
  - Recusa aceita justificativa (até 500 caracteres); aceitar e recusar compartilham a mesma regra com `POST /api/alocacoes/{id}/responder`.
- **E-mails** (Parte 4):
  - `Notificador.enviar(destinatario, assunto, template, variaveis)`: HTML e texto (multipart), com Thymeleaf em dois motores separados.
  - Templates em `resources/templates/email/`: `convite`, `lembrete-resposta`, `lembrete-servico`, `substituicao`, `aviso-coordenador`. Botões grandes, sem imagem externa nem JavaScript.
  - Envio só depois do commit (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async` no executor `email-`). Falha de SMTP só é logada; a escala não é desfeita.
  - Antes de enviar, grava a linha em `notificacao_enviada`; se já existe, não envia de novo (exceto o reenvio, que grava no próprio fluxo).
  - Avisos ao coordenador: recusa, expiração e vaga sem elegíveis.
  - `docker-compose.yml` sobe o Mailpit (SMTP 1025, painel em 8025) e o `app` aponta para ele. `.env.example` criado com valores falsos.
- **Jobs** (Parte 5), desligados com `servio.jobs.enabled=false` (ligados por padrão; desligados no perfil de teste):
  - `ExpiracaoConvitesJob` (a cada 10 min): expira convites pendentes vencidos com UPDATE condicional e avisa o coordenador uma vez. Rodar duas vezes não avisa duas.
  - `LembretesJob` (a cada 15 min): lembrete de resposta na janela `horasAntesDoPrazo` da pastoral (padrão 6h), que troca o token para o link valer; lembrete de serviço para quem aceitou, na janela `horasAntes` (padrão 24h). Cada lembrete sai uma vez por convite.
- **Painel do coordenador e `/api/me`** (Parte 6):
  - `GET /api/pastorais/{id}/painel?mes=AAAA-MM`: cards (vagas totais, ocupadas, convites pendentes, alterações pendentes), celebrações do mês com status `NAO_INICIADO`, `PENDENTE` ou `COMPLETO`, e pendências (convites no prazo, recusas e expirações sem substituto, alterações do vice aguardando).
  - Quem vê: COORDENADOR, VICE, SECRETARIO, TESOUREIRO, PADRE e ADMIN. MEMBRO recebe 403; pastoral de outra paróquia ou fora do alcance recebe 404.
  - `GET /api/me` passa a trazer `pastorais: [{id, nome, papel}]`.
- **Testes** (`*IT` com Testcontainers): `SorteioServiceTest`, `TokenConviteTest`, `SorteioIT`, `EscalacaoManualIT`, `ConvitePublicoIT`, `NotificacaoIT`, `JobsIT`, `PainelIT`, além de linhas novas em `EntrePastoraisIT`, `EntreParoquiasIT` e `PermissaoPorPapelIT`. Na última rodada completa: 58 unitários e 144 de integração, sem falhas.
- **Corrigido na etapa 7**: os e-mails nunca chegavam a ser enviados (o motor Thymeleaf exigia OGNL, o prefixo dos templates estava errado e a mensagem não era multipart). Os ITs mockam o `Notificador`, por isso não pegaram; `EmailTemplatesTest` e `EmailNotificadorTest` agora renderizam e montam a mensagem de verdade.

### Front-end React/PWA (etapa 7)
- **Rotas de leitura no backend** (Parte 1), com nomes já resolvidos e poucas consultas (`LeituraIT` conta os statements do Hibernate com 20 alocações):
  - `GET /api/me/escalas?de=&ate=` (padrão hoje até +60 dias), só do usuário logado.
  - `GET /api/pastorais/{id}/celebracoes/{celebracaoId}/escala`, com a mesma permissão do painel.
  - `GET /api/pastorais/{id}/membros` (e-mail só para COORDENADOR, PADRE e ADMIN) e `GET /api/usuarios/busca?nome=`.
  - Filtros `GET /api/celebracoes?de=&ate=` e `GET /api/funcoes?pastoralId=`; `GET /api/indisponibilidades` devolve só as próprias para quem não é PADRE nem ADMIN.
  - OpenAPI (springdoc) em `/v3/api-docs` e Swagger UI, ligados só com `OPENAPI_ENABLED=true`.
- **Front em `frontend/`**: Vite + React 19 + TypeScript strict, Node 22 (`.nvmrc`), React Router, TanStack Query, react-hook-form + zod. Cliente da API gerado pelo Orval (`npm run gen:api`, código gerado commitado) sobre um cliente HTTP único: CSRF, `ProblemDetail` virando `ErroApi` e 401 levando ao login (exceto o de `/api/me`, que só quer dizer "ninguém logado").
- **Tema**: tokens em CSS, contraste mínimo de 4,5:1, cor por pastoral só no front (`pascom` azul, `ecc` vermelho, demais por id numa paleta fixa) com `textoSobre(cor)` testado.
- **Telas**:
  - Público: entrar, página do convite (`/convite#token`, apaga o fragmento da URL, todos os estados: confirmado, recusado, 410, 404, 429, sem conexão), 404 e erro geral.
  - Servidor: minhas escalas (responder convite, aba de passadas) e indisponibilidades.
  - Gestão da pastoral: painel (Sortear em missa, Escalar em evento, Definir vagas sem quantidade, aprovar/desfazer alteração do vice), escala da celebração (sortear celebração ou vaga, escalar com motivos, escalar mesmo assim, substituir, reenviar), membros (buscar, criar conta de SERVIDOR, mudar papel, remover), configurações geradas do catálogo de regras, reuniões e financeiro.
  - Padre: celebrações (calendário e lista), responsabilizar pastoral, pastorais e financeiro consolidado.
  - Conta: trocar senha e troca de pastoral (salva no `localStorage`).
- **PWA**: manifest e ícones gerados de um SVG, aviso de nova versão, convite para instalar depois do primeiro login, service worker com `NetworkFirst` só para `GET /api/me/escalas` e aviso de sem conexão.
- **Publicação**: `frontend/Dockerfile` (Node → nginx sem root) e `nginx.conf` (fallback da SPA, `/api` para `app:8080` com `X-Forwarded-*`, gzip, cache longo só para arquivos com hash, CSP sem `unsafe-inline`/`unsafe-eval`). No `docker-compose.yml`, o serviço `web` fica na porta 80 e o `app` deixa de publicar porta.
- **CI**: job `frontend` (lint, formatação, tipos, testes, build), Dependabot para `npm` e `docker` em `frontend/` e workflow `e2e.yml` manual (`workflow_dispatch`).
- **Testes**: Vitest + Testing Library + MSW nas telas, axe nas páginas principais e Playwright de ponta a ponta contra o `docker compose` com Mailpit (a coordenadora sorteia, o teste abre o link do e-mail, a servidora confirma e o painel mostra "Completo").

## Em desenvolvimento
- Ligar `AuditLogService` às regras de negócio (login já audita; as demais ações ainda não). `Notificador` já é usado pela escalação e pelas reuniões.
- Fluxo de `PedidoTroca` (transições de status, só solicitante cancela, só destinatário aceita/recusa).
- Impedir que um COORDENADOR crie/promova alguém a `ADMIN` (ver `erros-conhecidos.md`, item 13).

Ver também: `features-futuras.md` e `erros-conhecidos.md`.
