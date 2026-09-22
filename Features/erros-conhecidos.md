# Erros e problemas conhecidos

Levantados em 2026-09-21 a partir do código e dos testes locais. Severidade: alta / média / baixa.

## Abertos

| # | Severidade | Problema | Onde | Sugestão |
|---|---|---|---|---|
| 1 | Alta | **Nenhum usuário consegue logar**: a tabela `usuario` está vazia e `POST /api/usuarios` também exige autenticação. | `SecurityConfig`, banco | Seed do primeiro ADMIN (ver `features-futuras.md`). |
| 2 | Alta | **Sem isolamento entre paróquias**: `CrudService.listar()/buscar()` retornam dados de qualquer paróquia para qualquer usuário autenticado. | `CrudService` | Filtrar pela paróquia do usuário logado. |
| 3 | Média | **Rota inexistente retorna 500** em vez de 404: `NoResourceFoundException` cai no handler genérico. | `GlobalHandleException` | Tratar `NoResourceFoundException` (e `HttpRequestMethodNotSupportedException`). |
| 4 | Média | **`AuditLogService.registrar` e `Notificador` nunca são chamados** por nenhum service; a auditoria e os e-mails não acontecem. | services | Ligar às regras de negócio. |
| 5 | Média | **`PedidoTroca` sem regras**: qualquer status/solicitante é aceito, sem validar que a alocação pertence ao solicitante. | `PedidoTrocaService` | Implementar o fluxo de troca. |
| 6 | Média | **Basic Auth ainda não aplicada no checkout/container**: o app em execução ainda usa a senha gerada do Spring (`Using generated security password`). | branch `worktree-basic-auth` | Copiar os 3 arquivos, `docker compose up -d --build app`. |
| 7 | Baixa | **Login ambíguo**: o e-mail só é único por paróquia; se repetir em outra paróquia, o login é recusado. | `UsuarioDetailsService` | E-mail + paróquia no login, ou e-mail único. |
| 8 | Baixa | **`PUT /api/usuarios/{id}` exige `senha` e sempre a troca** (re-hash a cada atualização). | `UsuarioService.resolverRelacoes` | Senha opcional na atualização; trocar só se informada. |
| 9 | Baixa | **Listagem carrega tudo e filtra `active` em memória** (`findAll().filter`). | `CrudService.listar` | Query `findByActiveTrue` + paginação. |
| 10 | Baixa | **E-mail não é enviado sem `spring.mail.host`**; só há `log.warn`, sem feedback ao usuário. | `EmailNotificador` | Configurar SMTP por ambiente; fila/retry. |
| 11 | Baixa | **Cobertura de testes mínima**: só `ServioApplicationTests` (carga de contexto). | `src/test` | Testes de services e integração. |
| 12 | Baixa | Senhas padrão `servio`/`servio` no compose e no `application.properties`. | compose, properties | Exigir variáveis em produção. |
| 13 | Alta | **Escalada de privilégio**: qualquer usuário logado cria usuários com `perfil: ADMIN` e em qualquer paróquia, pois a `SecurityConfig` só exige `authenticated()`. | `SecurityConfig`, DTOs | Etapas 1 e 3: matriz de permissões e paróquia vinda do usuário logado. |
| 14 | Alta | **Identidade vinda do JSON**: `solicitanteId`, `padreId` e `paroquiaId` são aceitos do cliente. | DTOs de request | Etapa 2: usar sempre o usuário autenticado. |
| 15 | Média | **Exclusão lógica x UNIQUE**: registro desativado bloqueia recadastro (ex.: realocar alguém removido da escala dá 409). | `V1__init.sql` | Etapa 2: índices únicos parciais `WHERE is_active`. |
| 16 | Média | **Validações só no criar**: o `PUT` pula as regras de negócio. | `CrudService` | Etapa 2: `validar()` no criar e no atualizar. |
| 17 | Média | **Sem controle de concorrência**: duas pessoas podem aceitar a mesma troca. | entidades | Etapa 2: `@Version`. |
| 18 | Baixa | **Senha aceita até 255 caracteres**, mas o BCrypt só usa os primeiros 72 bytes. | `UsuarioRequestDTO` | Etapa 2: `@Size(max = 72)`. |

## Resolvidos

| Problema | Resolução |
|---|---|
| `docker-compose.yml` com `DB_USER: postgres` e senha vazia no serviço `app` (o role `postgres` não existe; o próximo `up` falharia na autenticação). | Corrigido para `${POSTGRES_USER:-servio}` / `${POSTGRES_PASSWORD:-servio}`; app recriado e conectado. |
| App subia só com a segurança padrão do Spring (401 em tudo, senha gerada a cada start). | Basic Auth com a tabela `usuario` implementada e testada (ver item 6 para aplicar). |
| Basic Auth só existia na branch `worktree-basic-auth`. | Aplicado na `main`: `SecurityConfig` com `httpBasic` + `UsuarioDetailsService`. Será substituído por login com sessão na etapa 3. |

## Notas
- DataGrip "cannot parse url": costuma ser URL colada no campo Host ou com espaço/placeholder; usar host `localhost`, porta `5432`, database `servio`.
- O container antigo `financas-db` também mapeia a porta 5432; não subir junto com o `servio-db`.

Ver também: `features-implementadas.md` e `features-futuras.md`.
