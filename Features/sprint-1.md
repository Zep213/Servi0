# Servio — Sprint 1

**Período:** 26/10 a 08/11/2026 · **Back-end:** Zep · **Front-end:** colaborador

Stack: Spring Boot 4.1.1, Java 25, Maven wrapper, JPA/Hibernate, Lombok, MapStruct, Flyway, PostgreSQL 17,
Spring Session JDBC, Spring Security com `formLogin`, Thymeleaf para e-mails, Testcontainers nos ITs (`*IT`).

Detalhado a partir do combinado em `sprint-0.md` (seção 5) — ver também esse documento.

## Regras de trabalho

- [ ] Antes de codar cada cartão: ler as classes citadas nele e mostrar o plano antes de começar
- [ ] Um branch por cartão, a partir do `main`: `feat/fila-email`, `feat/token-lembrete`,
      `feat/telefone-conta`, `feat/login-telefone`, `feat/sessao-por-cargo`, `feat/api-me`
- [ ] Commits pequenos, mensagem em português
- [ ] Migrations novas a partir de `V14` (nunca editar migration que já existe)
- [ ] `./mvnw verify` ao fim de cada cartão (precisa do Docker ligado); não passar para o próximo com teste vermelho
- [ ] Não mudar comportamento fora do que está descrito em cada cartão; o que parecer errado no caminho vai pra
      "Pendente/decisões" no fechamento, não é corrigido de lado
- [ ] Nunca logar token de convite, senha nem corpo de e-mail
- [ ] Comentários e mensagens de erro em português, no mesmo estilo do código atual

## Escopo (6 cartões, nesta ordem)

### Cartão 1 — Fila de e-mails (outbox)

Problema: `NotificacaoListener` envia com `@Async` depois do commit. Se o SMTP falha ou o app reinicia, o
e-mail se perde, e o `notificacao_enviada` já ficou gravado como enviado.

Ler: `service/notification/{NotificacaoListener, EnvioEscalacao, Notificador, EmailNotificador}`,
`config/AsyncConfig`, `service/escalacao/job/{LembretesJob, ExpiracaoConvitesJob}`,
`service/ReuniaoService` (método `solicitar`), `model/NotificacaoEnviada`, `model/PastoralConfig`
(exemplo de `Map` em `jsonb` com `@JdbcTypeCode(SqlTypes.JSON)`).

- [ ] `V14__email_saida.sql`: tabela `email_saida` — `id BIGSERIAL`, `paroquia_id BIGINT NULL REFERENCES paroquia`,
      `destinatario VARCHAR(255) NOT NULL`, `assunto VARCHAR(255) NOT NULL`, `template VARCHAR(60) NULL`
      (`NULL` = texto simples em `variaveis.texto`), `variaveis JSONB`,
      `status VARCHAR(10) NOT NULL DEFAULT 'PENDENTE'` com `CHECK` (`PENDENTE`, `ENVIADO`, `FALHOU`),
      `tentativas SMALLINT NOT NULL DEFAULT 0`, `proxima_tentativa_em TIMESTAMP NOT NULL DEFAULT now()`,
      `ultimo_erro VARCHAR(500)`, `criado_em TIMESTAMP NOT NULL DEFAULT now()`, `enviado_em TIMESTAMP`;
      índice parcial `(proxima_tentativa_em) WHERE status = 'PENDENTE'`
- [ ] Enum `StatusEmail`; entidade `EmailSaida` (não é `TenantEntity`: sem `is_active`/`version`);
      `EmailSaidaRepository` com `travarPendente(id)` (nativa, `for update skip locked`),
      `idsProntos(status, agora, Pageable)` (mais antigos primeiro) e `apagarEnviadosAntesDe(limite)`
- [ ] Componente `FilaEmail` (`service/notification`):
  - [ ] `enfileirar(paroquiaId, destinatario, assunto, template, variaveis)` e
        `enfileirarTexto(paroquiaId, destinatario, assunto, texto)`
  - [ ] Com transação ativa (`TransactionSynchronizationManager.isActualTransactionActive()`): grava na
        transação do chamador e registra `afterCommit` que manda `tentar(id)` no `emailExecutor` (se o
        executor recusar, só loga: o job pega depois)
  - [ ] Sem transação (listeners `@Async` e jobs): grava numa transação própria e chama `tentar(id)` na hora,
        na mesma thread
  - [ ] `tentar(id)`: transação `REQUIRES_NEW`, trava a linha com `travarPendente`; vazio → sai. `template`
        nulo → `notificador.notificar(dest, assunto, variaveis.texto)`; senão `notificador.enviar(...)`.
        Sucesso: `ENVIADO`, `enviado_em` = agora, `variaveis = null` (atribuir `null` ao campo, **não** chamar
        `clear()` no `Map` — os testes capturam essa referência com `ArgumentCaptor`). Falha: `tentativas + 1`,
        `ultimo_erro` = classe + mensagem da causa raiz (cortado em 500), próxima tentativa em +1, +5, +15 e
        +60 minutos; na 5ª falha, `FALHOU` e `variaveis = null`. Log sem destinatário completo, sem token e
        sem corpo
  - [ ] Usar o `Clock` do contexto (os testes trocam o relógio)
- [ ] `EnvioEmailJob` (`@ConditionalOnProperty servio.jobs.enabled`, igual aos outros jobs):
  - [ ] a cada 1 minuto (`fixedDelay`, para não sobrepor): lote de 50 `idsProntos` → `fila.tentar(id)` um a um
  - [ ] 1 vez por dia de madrugada: apaga `ENVIADO` com mais de 30 dias
- [ ] `EnvioEscalacao`: troca `enviarSeguro(...)` por `enfileirar(paroquiaId, ...)` chamando a `FilaEmail`;
      deixa de depender do `Notificador`. `avisarCoordenadores` passa o `paroquiaId`. Atualizar
      `NotificacaoListener` e `LembretesJob`
- [ ] `ReuniaoService.solicitar`: troca `notificador.notificar` por `filaEmail.enfileirarTexto` (dentro da
      transação do pedido: o e-mail só sai se o pedido comitar)
- [ ] Javadoc da `FilaEmail` documentando a lacuna que sobra: entre o commit da ação e o listener `@Async`
      gravar na fila, o evento vive só em memória (milissegundos). Fechar isso exige mudar os listeners para
      gravar na fila antes do commit; fica para depois

Cuidados com testes existentes:

- [ ] `NotificacaoIT`/`JobsIT` (mockam `Notificador`, `verify(...).timeout(...)`) continuam passando: nos
      listeners e jobs a primeira tentativa é imediata; variáveis chegam ao mock como String/null (ida e
      volta pelo JSON), os asserts atuais valem
- [ ] `ReuniaoIT` é `@Transactional`: o `afterCommit` nunca roda, então `verify(notificador).notificar` falha.
      Trocar por consultas à `email_saida` (2 linhas `PENDENTE`, uma por coordenador, assunto "Pedido de
      reunião" e o motivo no texto; sem coordenador → nenhuma linha)
- [ ] `application-test.properties` continua com `servio.jobs.enabled=false`

Testes novos — `FilaEmailIT` (sem `@Transactional`, `Notificador` mockado, `Clock` controlável como no `JobsIT`):

- [ ] SMTP falha na 1ª tentativa e o e-mail sai na seguinte (avançar o relógio 1 minuto e rodar o job)
- [ ] Depois de 5 falhas fica `FALHOU`, com `variaveis` nula
- [ ] Depois do envio, `variaveis` fica nula e `enviado_em` preenchido
- [ ] Duas chamadas simultâneas de `tentar(id)` enviam uma vez só

### Cartão 2 — Lembrete sem invalidar o link do convite

Problema: o `LembretesJob` gera token novo e sobrescreve `alocacao.token_hash`; o link do primeiro e-mail
morre. Decisão: guardar os dois últimos tokens na própria alocação (não criar tabela nova).

Ler: `model/Alocacao`, `repository/AlocacaoRepository` (`findByTokenHashAndActiveTrue`, `expirarSePendente`),
`service/escalacao/{ConviteService, ConfirmacaoPublicaService, RespostaConviteService, EscalacaoService}`
(método `substituir`, ~linha 181), `LembretesJob`, `NotificacaoListener.tokenAtual`.

- [ ] `V15__alocacao_token_anterior.sql`: coluna `token_hash_anterior CHAR(64)` + índice único parcial
      (`WHERE NOT NULL`), igual ao `uq_alocacao_token` da `V12`
- [ ] `Alocacao`: campo `tokenHashAnterior` (mesmo mapeamento `CHAR` do `tokenHash`); métodos
      `invalidarTokens()` (zera os dois) e `girarToken(novoHash)` (anterior = atual, atual = novo)
- [ ] Repositório: `findByTokenValido(hash)` → `active = true and (tokenHash = :h or tokenHashAnterior = :h)`.
      `ConfirmacaoPublicaService` passa a usar este método. `expirarSePendente` zera as duas colunas
- [ ] `LembretesJob`: usa `girarToken` no lugar de `setTokenHash`
- [ ] `ConviteService.emitir` (convite e reenvio manual): grava o novo hash e zera o anterior (reenvio
      continua matando o link antigo de propósito)
- [ ] `RespostaConviteService` e `EscalacaoService.substituir`: `invalidarTokens()` no lugar de
      `setTokenHash(null)`
- [ ] `NotificacaoListener.tokenAtual` continua comparando só com o `token_hash` atual (evento antigo não
      reenvia)

Testes:

- [ ] `JobsIT`: renomear `lembreteDeRespostaSegueAConfigDaPastoralETrocaOToken`; além do que já verifica,
      conferir que o token antigo ainda abre o convite (GET público por token) e que o novo também
- [ ] Depois de responder, os dois tokens param (404/"não disponível")
- [ ] Depois de expirar, os dois param. Depois do reenvio manual, só o novo vale
- [ ] Ajustar os asserts de `getTokenHash` que mudarem (`ConvitePublicoIT`, `EscalacaoManualIT`, `JobsIT`)

### Cartão 3 — Telefone e aparência na conta

Ler: `model/Usuario`, `repository/UsuarioRepository`, migration `V2` (`uq_usuario_email_ativo`).

- [ ] `V16__usuario_telefone_aparencia.sql`: `telefone VARCHAR(20) NULL`,
      `aparencia VARCHAR(12) NOT NULL DEFAULT 'AUTOMATICO'`, `telefone_pulos SMALLINT NOT NULL DEFAULT 0`,
      `CHECK` de aparência (`AUTOMATICO`, `CLARO`, `ESCURO`), `CHECK` de telefone
      (`NULL` ou `'^\+55[1-9][0-9]{9,10}$'`), índice único parcial `uq_usuario_telefone_ativo` (`telefone`)
      `WHERE is_active AND telefone IS NOT NULL`
- [ ] Enum `Aparencia`; campos em `Usuario` (`telefone`, `aparencia` com default `AUTOMATICO`, `telefonePulos`)
- [ ] Classe utilitária `Telefone` (sem Spring) com `normalizar(String) → Optional<String>` no formato
      `+55DDNNNNNNNNN`:
  - [ ] tira tudo que não é dígito; tira zero à esquerda (`0 83 ...`); aceita 10 ou 11 dígitos (DDD + número)
        ou 12/13 começando com 55
  - [ ] DDD não começa com 0; número de 11 dígitos (celular) tem o 3º dígito igual a 9
  - [ ] `"(83) 9 8812-4407"`, `"83988124407"`, `"+55 83 98812-4407"` e `"083988124407"` viram
        `"+5583988124407"`; fixo `"(83) 3321-1234"` vira `"+558333211234"`; vazio, letras, 8 dígitos, DDD 00
        e celular sem 9 → vazio
  - [ ] `ehTelefone(String login)`: `true` se não tem `@` e tem pelo menos 10 dígitos
- [ ] `UsuarioRepository`: `findByTelefoneAndActiveTrue`, `existsByTelefoneAndActiveTrueAndIdNot`
- [ ] Cadastro e edição de usuário (`UsuarioRequestDTO`/`UpdateDTO`, mapper) **não** ganham telefone nesta
      sprint: só o próprio usuário define o dele pelo `PUT /api/me` (cartão 6). Confirmar no `MassAssignmentIT`

Teste: `TelefoneTest` (unitário, sem Spring) com os formatos válidos e inválidos acima.

### Cartão 4 — Login por e-mail ou telefone

Ler: `security/{UsuarioDetailsService, UsuarioPrincipal, LoginHandlers, LoginRateLimitFilter, TentativasLogin,
AuditoriaLoginListener, SessaoService}`, `config/SecurityConfig`, testes
`seguranca/{ApoioTeste, CsrfIT, LoginIT}`.

- [ ] `SecurityConfig`: `usernameParameter("login")` (era `"email"`); remover a linha duplicada de
      `/actuator/health`
- [ ] `UsuarioDetailsService.loadUserByUsername(login)`: com `@` busca por e-mail (como hoje); sem `@`,
      normaliza com `Telefone` e busca por `findByTelefoneAndActiveTrue`; inválido →
      `UsernameNotFoundException`. `UsuarioPrincipal.getUsername()` continua devolvendo o **e-mail**
      (`SessaoService.encerrarTodas` acha as sessões por ele)
- [ ] Componente `ChaveLogin`: dado o texto digitado, devolve as chaves de rate limit —
      `"login:<email minúsculo>"` ou `"login:<telefone normalizado>"` (texto inválido: o próprio texto
      aparado e minúsculo) e, se a conta existir, também `"conta:<id>"`. E-mail e telefone da mesma pessoa
      somam as tentativas. Substitui `TentativasLogin.chaveEmail`
- [ ] `LoginRateLimitFilter`: lê o parâmetro `"login"`; bloqueia se o IP ou qualquer chave do `ChaveLogin`
      estiver bloqueada. Criado à mão no `SecurityConfig` (não é bean): passar o `ChaveLogin` no construtor
- [ ] `LoginHandlers`: falha registra o IP e todas as chaves do `ChaveLogin`; sucesso limpa IP,
      `"conta:<id>"`, chave do e-mail e chave do telefone da conta. Resposta 401 vira
      `"E-mail, telefone ou senha inválidos"` (a mesma, exista a conta ou não)
- [ ] `AuditoriaLoginListener.aoFalhar`: acha o usuário por e-mail ou telefone (mesma regra do item acima);
      `log.warn` não mostra o telefone inteiro (mascarar, ex.: `+55839****4407`)
- [ ] Front (`frontend/src/auth/api.ts`): formulário envia `"login"` no lugar de `"email"` (só o nome do
      campo; a tela nova é do colaborador); rodar os testes do front

Testes:

- [ ] `ApoioTeste.login`, `CsrfIT` e `LoginIT`: trocar `.param("email", ...)` por `.param("login", ...)`
- [ ] `LoginIT` novos: entra com telefone em 3 formatos diferentes e cai na mesma conta (`/api/me` devolve o
      mesmo id); telefone inexistente dá 401 com a mesma mensagem; 3 falhas pelo e-mail + 2 pelo telefone da
      mesma pessoa → a próxima tentativa (por qualquer um dos dois) dá 429

### Cartão 5 — Duração da sessão por cargo

Ler: `LoginHandlers`, `SecurityConfig` (`cookieSerializerCustomizer`), `application.properties`
(`spring.session.timeout=7d`), `UsuarioPastoralRepository.findByUsuarioIdAndActiveTrue`.

- [ ] Propriedades `servio.sessao.membro-dias=60` e `servio.sessao.gestao-dias=7`
- [ ] `LoginHandlers.sucesso`: `request.getSession().setMaxInactiveInterval(...)` — 60 dias se o perfil é
      `SERVIDOR` e todos os papéis ativos são `MEMBRO` ou `REDES_SOCIAIS` (ou nenhum papel); 7 dias se tem
      qualquer outro papel (`COORDENADOR`, `VICE`, `SECRETARIO`, `TESOUREIRO`, `TECNICO`) ou perfil
      `PADRE`/`ADMIN`. (A troca de id da sessão por session fixation acontece antes do success handler —
      confirmar isso no teste.)
- [ ] Cookie do Spring Session hoje é de sessão (sem `Max-Age`) e some quando o navegador fecha: no
      `cookieSerializerCustomizer`, definir `setCookieMaxAge` com o maior prazo (60 dias) — quem manda de
      verdade é o `MAX_INACTIVE_INTERVAL` no banco

Testes (`SessaoIT`): membro loga → `spring_session.max_inactive_interval` = 60 dias e o `Set-Cookie` tem
`Max-Age`; coordenador → 7 dias; `PADRE` → 7 dias. O teste existente `rebaixarPerfilDerrubaASessao` continua
passando.

### Cartão 6 — `/api/me`

Ler: `controller/MeController`, `service/MeService`, `model/dto/{MeResponseDTO, PastoralPapelDTO}`,
`service/escalacao/PainelService` (`convitesVencendo`, `recusasSemSubstituto`, `alteracoesAguardando`),
`security/PastoraisPermissao`, `service/AuditLogService`.

`GET /api/me` (muda) — soma ao que já devolve:

```json
{
  "telefone": "+5583988124407",
  "aparencia": "AUTOMATICO",
  "pedirTelefone": false,
  "pastorais": [ { "id": 1, "nome": "Pascom", "papel": "COORDENADOR", "pendencias": 4 } ]
}
```

- [ ] `telefone`: `null` se não tem
- [ ] `pedirTelefone`: `true` se telefone nulo e `telefone_pulos < 3`
- [ ] O controller passa a montar a resposta pelo `MeService` (telefone e aparência vêm do banco, não do
      principal)
- [ ] `pendencias`: para quem tem acesso ao painel daquela pastoral (mesma regra de permissão do
      `PainelController`), soma de convites vencendo + recusas sem substituto + alterações do vice
      aguardando. Expor no `PainelService` um método público de contagem que reaproveite as consultas
      privadas (não duplicar a lógica). Para membro: quantos convites `PENDENTE` dele naquela pastoral
- [ ] Fora do escopo: `fotoUrl` (sprint 2) e `avisosNaoLidos` (sprint 5)

`PUT /api/me` (novo) — corpo `MeUpdateDTO { telefone?, aparencia?, pularTelefone? }`, todos opcionais:

- [ ] Só altera o que vier; telefone `""` ou `null` explícito não apaga nesta sprint (ignorar — apagar fica
      para depois)
- [ ] Telefone inválido → 400 "Telefone inválido". Já usado por outra conta ativa → 409 "Este telefone não
      pode ser usado" (genérico, não diz que existe conta). `DataIntegrityViolation` do índice único (duas
      pessoas ao mesmo tempo) também é 409
- [ ] `pularTelefone: true` soma 1 em `telefone_pulos` (só se telefone ainda for nulo)
- [ ] Mudança de telefone grava `AuditLog` "TELEFONE_ALTERADO" com o detalhe mascarado
- [ ] Devolve o mesmo corpo do GET
- [ ] `/api/me` (sem barra) também cobertas pelo `SecurityConfig` (hoje só `/api/me/**`)
- [ ] `fail-on-unknown-properties` continua valendo: campo a mais no JSON é 400

Testes (`MeIT`): GET devolve os campos novos; PUT normaliza o telefone; PUT com telefone de outra conta →
409; 3 pulos → `pedirTelefone` false; aparência `ESCURO` persiste; pendências do coordenador e do membro com
dados montados pelo `CenarioParoquia`/`CenarioEscalacao`; campo desconhecido → 400.

- [ ] Ao terminar o cartão, rodar o OpenAPI (`OPENAPI_ENABLED=true`) e confirmar que `/v3/api-docs` mostra o
      GET e o PUT novos, para o front rodar `npm run gen:api`

## Contrato combinado (ver `sprint-0.md`, seção 5)

- [ ] `GET /api/me`: campos novos `telefone`, `aparencia`, `pedirTelefone` e `pastorais[].pendencias`
- [ ] `PUT /api/me`: `{ telefone?, aparencia?, pularTelefone? }`; 409 para telefone já usado
- [ ] `POST /api/auth/login`: campo do formulário muda de `email` para `login`
- [ ] Mensagens de erro do front (telefone inválido, telefone em uso, login inválido, muitas tentativas)

## Fechamento da sprint

- [ ] Atualizar `Features/features-implementadas.md` e `Features/erros-conhecidos.md` com o que mudou
- [ ] Resumo por cartão: o que mudou, testes novos, e qualquer coisa que ficou pendente ou decidida diferente
      do planejado (com o motivo)

## Pendente de detalhar

- [ ] Quebra em cartões já feita nesta versão do documento — falta só o quadro (GitHub Projects/Trello) e os
      dois rituais de 30 min (ver `sprint-0.md`, seção 1)
- [ ] Link do protótipo e do mapa de endpoints (ainda não recebidos)

## Casca nova no front

- [ ] Barra lateral, sininho, avatar, seletor de aparência, calendário do mês — fora do escopo deste
      detalhamento (trabalho do colaborador); ver `sprint-0.md`, seção 4

Ver também: `sprint-0.md`.
