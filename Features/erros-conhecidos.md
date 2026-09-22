# Erros e problemas conhecidos

Levantados em 2026-09-21 a partir do código e dos testes locais. Atualizado em 2026-09-22 (etapa 3: sessão, CSRF, rate limit e auditoria de login). Severidade: alta / média / baixa.

## Abertos

| # | Severidade | Problema | Onde | Sugestão |
|---|---|---|---|---|
| 4 | Média | **`Notificador` nunca é chamado** por nenhum service; e-mails não são enviados (o `AuditLogService`/auditoria de login já foi ligado, ver Resolvidos). | services | Ligar às regras de negócio. |
| 5 | Média | **`PedidoTroca` sem regras**: qualquer status é aceito, sem validar que só o destinatário aceita/recusa e só o solicitante cancela. | `PedidoTrocaService` | Implementar o fluxo de troca (etapa futura). |
| 10 | Baixa | **E-mail não é enviado sem `spring.mail.host`**; só há `log.warn`, sem feedback ao usuário. | `EmailNotificador` | Configurar SMTP por ambiente; fila/retry. |
| 12 | Baixa | Senhas padrão `servio`/`servio` no compose e no `application.properties`. | compose, properties | Exigir variáveis em produção. |
| 13 | Alta | **Escalada de privilégio de perfil, parte 2**: a rota `POST/PUT /api/usuarios` já exige ADMIN/COORDENADOR (ver Resolvidos), mas nada impede um COORDENADOR de criar/promover alguém a `ADMIN` — o valor de `perfil` no request não é validado contra o perfil de quem está chamando. | `UsuarioService`, `SecurityConfig` | `validar()`/regra de negócio: só ADMIN pode atribuir perfil ADMIN. |

## Resolvidos

| Problema | Resolução |
|---|---|
| `docker-compose.yml` com `DB_USER: postgres` e senha vazia no serviço `app` (o role `postgres` não existe; o próximo `up` falharia na autenticação). | Corrigido para `${POSTGRES_USER:-servio}` / `${POSTGRES_PASSWORD:-servio}`; app recriado e conectado. |
| App subia só com a segurança padrão do Spring (401 em tudo, senha gerada a cada start). | Basic Auth com a tabela `usuario` implementada; depois substituída por login com sessão (ver abaixo). |
| **Nenhum usuário conseguia logar**: tabela `usuario` vazia e todo endpoint (inclusive `POST /api/usuarios`) exige autenticação. | `BootstrapAdmin` cria a primeira `Paroquia` + o primeiro `ADMIN` a partir de `SERVIO_ADMIN_EMAIL`/`SENHA`/`NOME` e `SERVIO_PAROQUIA_NOME`. |
| **Basic Auth**: senha gerada a cada start, sem sessão, sem logout, sem rate limit. | Etapa 3: `httpBasic` removido; login por sessão (Spring Session JDBC), rate limit de força bruta, CSRF real, auditoria de login e troca da própria senha — ver `features-implementadas.md`. |
| **Escalada de privilégio de rota**: qualquer usuário logado podia chamar `POST /api/usuarios` e criar um `ADMIN` em qualquer paróquia, pois a `SecurityConfig` só exigia `authenticated()`. | `SecurityConfig` reescrito com RBAC por perfil (`hasRole`/`hasAnyRole`) em todas as rotas; regra padrão `anyRequest().denyAll()` (rota nova nasce bloqueada). Confirmado por `AutorizacaoIT` (matriz de permissões). |
| **Rota inexistente retornava 500** em vez de 404 (`NoResourceFoundException` caindo no handler genérico). | `GlobalHandleException` ganhou handlers para `NoResourceFoundException` (404), `HttpRequestMethodNotSupportedException` (405), `HttpMessageNotReadableException` (400) e `AccessDeniedException` (403). |
| **Sem isolamento entre paróquias**: `CrudService.listar()/buscar()` retornavam dados de qualquer paróquia para qualquer usuário autenticado. | `TenantEntity` + `TenantRepository` + `CrudService` reescritos: toda consulta filtra por `paroquiaId()` do usuário logado; `criar()` seta `paroquiaId` automaticamente, nunca vindo do request. |
| **Identidade vinda do JSON**: `solicitanteId`, `padreId` e `paroquiaId` eram aceitos do cliente nos DTOs de request. | Campos removidos dos DTOs; `CompromissoAgendaService`/`PedidoTrocaService` usam sempre `usuarioId()` do usuário logado; `paroquiaId` é preenchido pelo `CrudService` a partir do `UsuarioLogado`. |
| **Login ambíguo**: o e-mail só era único por paróquia; se repetisse em outra paróquia, o login era recusado. | `V2` cria índice único parcial `uq_usuario_email_ativo` (e-mail único entre ativos no sistema todo); `UsuarioDetailsService` usa `findByEmailIgnoreCaseAndActiveTrue`. |
| **`PUT /api/usuarios/{id}` exigia `senha` e sempre a trocava** (re-hash a cada atualização). | Novo `UsuarioUpdateDTO` com `senha` opcional; `UsuarioService.atualizar` só re-hasheia se vier preenchida. |
| **Listagem carregava tudo e filtrava `active` em memória** (`findAll().filter`). | `CrudService.listar(Pageable)` usa `findByParoquiaIdAndActiveTrue` com paginação real; controllers expõem `?page=&size=&sort=` via `@PageableDefault`. |
| **Exclusão lógica x UNIQUE**: registro desativado bloqueava recadastro (ex.: realocar alguém removido da escala dava 409). | `V2` troca as `UNIQUE` constraints antigas por índices únicos parciais `WHERE is_active` (usuário/e-mail, usuário-função, alocação). Testado: desativar usuário e recriar com o mesmo e-mail agora dá 201, não mais 409. |
| **Validações só rodavam no criar**: o `PUT` pulava as regras de negócio. | `CrudService.validar(E entidade)` agora roda tanto em `criar()` quanto em `atualizar()`. |
| **Sem controle de concorrência**: duas pessoas podiam alterar o mesmo registro sem aviso (ex.: aceitar a mesma troca). | `@Version` em todas as entidades tenant (`V2`); `GlobalHandleException` trata `ObjectOptimisticLockingFailureException` como 409 com mensagem amigável. |
| **Senha aceita até 255 caracteres**, mas o BCrypt só usa os primeiros 72 bytes. | `UsuarioRequestDTO`/`UsuarioUpdateDTO` com `@Size(max = 72)`. |
| **Migration `V2` falhava no `flyway migrate`** com `relation "idx_usuario_paroquia" already exists`. | A `V1__init.sql` já criava esse índice (junto com `usuario.paroquia_id`, que já existia desde o início). A seção de índices da `V2` duplicava a criação; removida a linha redundante. |
| **Bug silencioso e crítico: `save()` de entidade nova não preenchia o `id` no objeto original** (ex.: `paroquiaRepository.save(paroquia)` seguido de `paroquia.getId()` retornava `null`, mesmo com o INSERT já confirmado no banco). | Causa: `ActivatableEntity.version` tinha `= 0L` como valor default do campo Java. Isso quebra a heurística `isNew()` do Spring Data para entidades com `@Version` (que considera "nova" só quando `version == null`); toda entidade recém-criada era tratada como já existente, e `save()` chamava `merge()` em vez de `persist()` — `merge()` devolve uma cópia gerenciada separada, sem mutar o objeto original. Corrigido removendo o `= 0L`; o Hibernate atribui `version = 0` automaticamente no insert. Encontrado ao testar o `BootstrapAdmin` (admin falhava com `paroquia_id` nulo). |
| **Cobertura de testes mínima**: só `ServioApplicationTests` (carga de contexto). | Etapa 3: 7 suítes de integração (`AutorizacaoIT`, `LoginIT`, `SessaoIT`, `CsrfIT`, `TenantIsolamentoIT`, `MassAssignmentIT`, `VazamentoIT`; 23 testes) rodando contra Postgres real via Testcontainers. |
| **`server.servlet.session.cookie.*` não tinha nenhum efeito** sobre o cookie do Spring Session (cookie saía com o nome padrão `SESSION`, não `SERVIO_SESSION`). | Nessa versão do Spring Boot, sessão virou módulo próprio (`spring-boot-session`), que só expõe `spring.session.servlet.*` (sem nada de cookie). Corrigido com um bean `DefaultCookieSerializerCustomizer` em `SecurityConfig`, que aplica nome/httpOnly/secure/sameSite de verdade a partir das mesmas properties. |
| **`LoginRateLimitFilter` era um no-op completo**: o rate limit de força bruta nunca disparava, nem na aplicação real. | `request.getServletPath()` retorna vazio porque o `DispatcherServlet` está mapeado em `/` (mapeamento padrão) — o path inteiro vai para `getRequestURI()`. Corrigido trocando a comparação para `getRequestURI()`. Achado rodando `LoginIT` de verdade contra Testcontainers, não só compilando. |
| **Testes `@SpringBootTest`+MockMvc+CSRF davam resultado diferente rodando juntos vs. isolados** (`CsrfIT` ora recebia o cookie `XSRF-TOKEN` esperado, ora um `SESSION` com header `X-CSRF-TOKEN` do fallback clássico). | Contaminação de estado entre classes de teste no mesmo fork do Surefire. Corrigido com `reuseForks=false` no `maven-surefire-plugin` (cada classe `@SpringBootTest` roda em uma JVM nova). |
| **`./mvnw verify` pulava silenciosamente todos os testes `*IT.java`** (Surefire só roda `*Test`/`*Tests` por padrão; `*IT` é convenção do Failsafe). | Adicionado `maven-failsafe-plugin` (goals `integration-test`+`verify`, também com `reuseForks=false`) ao `pom.xml`. Confirmado: `./mvnw verify` agora roda os 23 testes de integração de verdade. |

## Notas
- DataGrip "cannot parse url": costuma ser URL colada no campo Host ou com espaço/placeholder; usar host `localhost`, porta `5432`, database `servio`.
- O container antigo `financas-db` também mapeia a porta 5432; não subir junto com o `servio-db`.

Ver também: `features-implementadas.md` e `features-futuras.md`.
