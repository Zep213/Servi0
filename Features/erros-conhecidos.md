# Erros e problemas conhecidos

Levantados em 2026-09-21 a partir do código e dos testes locais. Atualizado em 2026-09-22 (etapa 2: multi-tenant e integridade). Severidade: alta / média / baixa.

## Abertos

| # | Severidade | Problema | Onde | Sugestão |
|---|---|---|---|---|
| 4 | Média | **`AuditLogService.registrar` e `Notificador` nunca são chamados** por nenhum service; a auditoria e os e-mails não acontecem. | services | Ligar às regras de negócio. |
| 5 | Média | **`PedidoTroca` sem regras**: qualquer status é aceito, sem validar que só o destinatário aceita/recusa e só o solicitante cancela. | `PedidoTrocaService` | Implementar o fluxo de troca (etapa futura). |
| 6 | Média | **Basic Auth ainda não aplicada no checkout/container**: o app em execução ainda usa a senha gerada do Spring (`Using generated security password`). | branch `worktree-basic-auth` | Copiar os 3 arquivos, `docker compose up -d --build app`. |
| 10 | Baixa | **E-mail não é enviado sem `spring.mail.host`**; só há `log.warn`, sem feedback ao usuário. | `EmailNotificador` | Configurar SMTP por ambiente; fila/retry. |
| 11 | Baixa | **Cobertura de testes mínima**: só `ServioApplicationTests` (carga de contexto), testado o resto manualmente via `curl`. | `src/test` | Testes de services e integração (Testcontainers). |
| 12 | Baixa | Senhas padrão `servio`/`servio` no compose e no `application.properties`. | compose, properties | Exigir variáveis em produção. |
| 13 | Alta | **Escalada de privilégio de perfil**: qualquer usuário logado ainda cria usuários com `perfil: ADMIN`, pois a `SecurityConfig` só exige `authenticated()` (a parte de paróquia já foi resolvida — ver item 14 em Resolvidos). | `SecurityConfig`, `UsuarioController`, `UsuarioRequestDTO` | `@EnableMethodSecurity` já habilitado; falta anotar os endpoints com `@PreAuthorize` por perfil. |

## Resolvidos

| Problema | Resolução |
|---|---|
| `docker-compose.yml` com `DB_USER: postgres` e senha vazia no serviço `app` (o role `postgres` não existe; o próximo `up` falharia na autenticação). | Corrigido para `${POSTGRES_USER:-servio}` / `${POSTGRES_PASSWORD:-servio}`; app recriado e conectado. |
| App subia só com a segurança padrão do Spring (401 em tudo, senha gerada a cada start). | Basic Auth com a tabela `usuario` implementada e testada (ver item 6 para aplicar). |
| Basic Auth só existia na branch `worktree-basic-auth`. | Aplicado na `main`: `SecurityConfig` com `httpBasic` + `UsuarioDetailsService`. Será substituído por login com sessão na etapa 3. |
| **Nenhum usuário conseguia logar**: tabela `usuario` vazia e todo endpoint (inclusive `POST /api/usuarios`) exige autenticação. | `BootstrapAdmin` cria a primeira `Paroquia` + o primeiro `ADMIN` a partir de `SERVIO_ADMIN_EMAIL`/`SENHA`/`NOME` e `SERVIO_PAROQUIA_NOME`. Testado com banco zerado: admin criado, login via Basic Auth e `GET /api/me` retornando 200. |
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

## Notas
- DataGrip "cannot parse url": costuma ser URL colada no campo Host ou com espaço/placeholder; usar host `localhost`, porta `5432`, database `servio`.
- O container antigo `financas-db` também mapeia a porta 5432; não subir junto com o `servio-db`.

Ver também: `features-implementadas.md` e `features-futuras.md`.
