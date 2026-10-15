# Servio — Sprint 5

**Período:** 04/01 a 17/01/2027 · **Back-end:** Zep · **Front-end:** colaborador

Troca e Início. **Pronto quando:** uma troca aceita muda a escala e o coordenador recebe o aviso.

Stack: Spring Boot 4.1.1, Java 25, Maven wrapper, JPA/Hibernate, Lombok, MapStruct, Flyway, PostgreSQL 17,
Spring Session JDBC, Spring Security com `formLogin`, Thymeleaf para e-mails, Testcontainers nos ITs (`*IT`).

Detalhado a partir de `features-futuras.md` (seção "Sprint 5" e "Decisões pendentes") e de `sprint-4.md`/`sprint-2.md`;
ver também `sprint-0.md`. Escopo desta sprint é **só back-end**: Início do membro, Escalas, Pedir troca,
Disponibilidade, sininho e a tela de Avisos são do colaborador (front).

## Pré-requisitos (o que já precisa estar na `main`)

- [ ] Sprints 1 a 4 mergeadas. Esta sprint usa, de lá: `FilaEmail` e `email_saida` (sprint 1), `TokenConvite` e o
      padrão de página pública sem login (convite, sprint 1; contas, sprint 2), `GET /api/me` com
      `pastorais[].pendencias` (sprint 1), o horário da celebração como limite do prazo (`ConviteService.novoPrazo`,
      sprint 4) e o horário fixo de missa (sprint 4)
- [ ] Numeração das migrations: a sprint 4 usa `V20` (horário de missa) e `V21` (origem do evento); as desta sprint
      começam em `V22`. Se alguma sprint anterior criar migration a mais, usar o próximo número livre da `main`
      (conferir `ls src/main/resources/db/migration` antes de nomear) e ajustar os nomes abaixo
- [ ] Domínio e remetente de e-mail (SPF, DKIM, DMARC) funcionando: o pedido de troca chega por e-mail, e a página
      pública depende do link do e-mail

## Regras de trabalho

- [ ] Antes de codar cada cartão: ler as classes citadas nele e mostrar o plano antes de começar
- [ ] Um branch por cartão, a partir do `main`: `feat/troca-estados`, `feat/avisos-app`, `feat/troca-responder`,
      `feat/troca-publica`, `feat/me-inicio`. Os cartões 3 e 4 dependem do 1 e do 2: empilhar no branch do cartão de
      que dependem e dizer isso no PR
- [ ] Commits pequenos, mensagem em português
- [ ] Migrations novas a partir de `V22` (nunca editar migration que já existe)
- [ ] `./mvnw verify` ao fim de cada cartão (precisa do Docker ligado); não passar para o próximo com teste vermelho
- [ ] Não mudar comportamento fora do que está descrito em cada cartão; o que parecer errado no caminho vai pra
      "Pendente/decisões" no fechamento, não é corrigido de lado
- [ ] Nunca logar token de troca, senha nem corpo de e-mail. Log de e-mail mostra no máximo o domínio do destinatário
- [ ] **Todo** e-mail desta sprint sai pela `FilaEmail` (nunca `Notificador` direto)
- [ ] Token só vai por e-mail e no **fragmento** da URL (`/troca#token`, como `/convite#token`). O banco guarda só o
      SHA-256
- [ ] Todo relógio vem do `Clock` do contexto; nada de `LocalDateTime.now()` direto
- [ ] Comentários e mensagens de erro em português, no mesmo estilo do código atual

## Padrão que os cartões seguem (já existe no código)

- **`PedidoTroca` hoje não tem regra nenhuma** (item 5 de `erros-conhecidos.md`): tabela `pedido_troca`
  (`alocacao_id`, `solicitante_id`, `destinatario_id` opcional, `status`) com `StatusTroca` `ABERTO`, `ACEITO`,
  `RECUSADO`, `CANCELADO`; `PedidoTrocaService` é só o `CrudService` genérico: `PUT /api/pedidos-troca/{id}` aceita
  qualquer `alocacaoId`/`destinatarioId`, e **nenhum endpoint muda o `status`**, nem muda a `Alocacao`. Não há
  testes nem tela que o usem; a rota já é `authenticated` no `SecurityConfig` (`/api/pedidos-troca/**`), então o
  contrato pode mudar sem quebrar ninguém
- **Trocar quem serve** já existe em outro lugar: `EscalacaoService.substituir` trava a vaga
  (`findByIdParaEscalarComLock`), confere `exigirNaoAlocado` e `exigirPosicaoLivre`, revalida o candidato com
  `ElegibilidadeService.impedimentos(usuario, vaga, alocacaoIgnoradaId)`, marca a alocação antiga `SUBSTITUIDA`
  (`substituidaEm`, `invalidarTokens()`) e cria a nova com `novaAlocacao`. A troca da sprint 5 reaproveita essas
  peças, sem o papel de coordenação e sem convite novo (quem aceita a troca já aceitou o serviço)
- **Regras de elegibilidade** (`service/escalacao/regra`): `DomingosSeguidos`, `IntervaloMinimo`, `SemIndisponibilidade`,
  `UmaVagaPorCelebracao` (+ `HABILIDADE` da sprint 3), cada uma ligada ou desligada pela pastoral
  (`ConfiguracaoPastoralService`). `forcar` só existe para a coordenação
- **Página pública do convite**: `ConfirmacaoController` em `/api/confirmacoes/**` (`permitAll`, CSRF ligado),
  `POST /detalhes` e `POST /responder` com `{ token }`, limitada por IP (`LimiteConfirmacoes`, 30 por minuto),
  token inexistente, usado ou de registro inativo dá **a mesma 404**, vencido dá 410, só o primeiro nome vai na
  resposta (`ConfirmacaoPublicaService`). O mesmo vale para a página de troca
- **E-mails e avisos à coordenação**: `EnvioEscalacao` (`enfileirar`, `avisarCoordenadores`, deduplicação em
  `notificacao_enviada` por alocação e tipo) e `NotificacaoListener` (eventos depois do commit). Hoje o aviso é
  **só por e-mail** (`aviso-coordenador.{html,txt}`); não existe aviso dentro do app
- **Jobs**: `ExpiracaoConvitesJob` (a cada 10 minutos, `UPDATE` condicional por item, cada item na própria
  transação, `servio.jobs.enabled`) é o modelo para a expiração das trocas
- **`GET /api/me`** já devolve `pastorais[].pendencias` e `GET /api/me/escalas` as escalas do próprio usuário;
  `avisosNaoLidos` ficou de fora da sprint 1 de propósito ("sprint 5")

## Escopo (5 cartões, nesta ordem)

### Cartão 1 — Pedido de troca com estados e expiração

Problema: o `PedidoTroca` aceita qualquer coisa e nunca muda a escala (ver acima). Decisão: a pessoa escalada pede a
troca a **uma pessoa escolhida da própria pastoral**; o pedido tem estados, vence 24 horas antes do evento (decidido
em 14/10) e só uma pessoa por vez pode responder.

Ler: `model/PedidoTroca`, `model/enumerated/StatusTroca`, `service/PedidoTrocaService`, `controller/PedidoTrocaController`,
`mapper/PedidoTrocaMapper`, `repository/PedidoTrocaRepository`, `model/{Alocacao, Vaga, Celebracao}`,
`service/escalacao/{ConviteService, ConfiguracaoPastoralService, TokenConvite}`,
`service/escalacao/job/ExpiracaoConvitesJob`, `service/notification/{FilaEmail, EnvioEscalacao}`,
`config/SecurityConfig` (`/api/pedidos-troca/**`), templates `templates/email/substituicao.{html,txt}`,
`repository/UsuarioPastoralRepository`, `PastoraisPermissao`.

- [ ] `V22__pedido_troca_estados.sql`: em `pedido_troca`, `expira_em TIMESTAMP`, `respondido_em TIMESTAMP`,
      `token_hash CHAR(64)` (o link do destinatário; nulo depois da resposta) com índice único parcial
      `WHERE token_hash IS NOT NULL`, e índice único parcial `(alocacao_id) WHERE status = 'ABERTO' AND is_active`
      (um pedido aberto por alocação). `destinatario_id` continua nulável na tabela (pedidos antigos), mas passa a ser
      **obrigatório** no pedido novo (ver Pendente/decisões). `status` ganha o valor `EXPIRADO`
- [ ] `StatusTroca`: `ABERTO`, `ACEITO`, `RECUSADO`, `CANCELADO`, `EXPIRADO`. Transições permitidas (qualquer outra é
      422 "Este pedido já foi respondido"): `ABERTO → ACEITO | RECUSADO | CANCELADO | EXPIRADO`; os quatro destinos
      são finais. Toda transição é um `UPDATE ... SET status = :novo WHERE id = :id AND status = 'ABERTO'` (devolve
      0 ou 1): duas respostas ao mesmo tempo, uma só vence, a outra recebe 422
- [ ] `PedidoTrocaService` deixa de ser `CrudService`. Rotas (substituem o CRUD genérico; `PUT` e `DELETE` genéricos
      **saem**, porque permitiam mudar qualquer campo):
  - [ ] `POST /api/pedidos-troca` `{ alocacaoId, destinatarioId, motivo? }` → **201** com o pedido. Regras, todas com
        mensagem clara em 422 salvo onde indicado:
        a alocação é **do próprio usuário logado** (a de outra pessoa: 404, como se não existisse), está `ACEITA` e
        ativa; a celebração ainda não começou **e faltam mais de 24 horas** para o início (senão "Faltam menos de 24
        horas para o serviço: fale com a coordenação"); o destinatário é **outra pessoa**, ativa, **membro ativo da
        mesma pastoral** da vaga e da mesma paróquia (senão 404/422); não há outro pedido `ABERTO` para a alocação
        (409 "Já existe um pedido de troca aberto para este serviço")
  - [ ] `expira_em` = início da celebração − 24 horas (`servio.troca.antecedencia-horas=24`)
  - [ ] Emite o token (`TokenConvite.gerar()`, só o hash no banco) e enfileira o e-mail `troca-solicitada` para o
        **destinatário** (nome, quem pede em primeiro nome, celebração, data, hora, função, prazo para responder =
        `expira_em`, `link` = `servio.front-url` + `/troca#` + token). O e-mail **não** leva o motivo (texto livre da
        pessoa); o motivo aparece só dentro do app (ver Pendente/decisões)
  - [ ] `POST /api/pedidos-troca/{id}/cancelar` → **204**, só o solicitante, só `ABERTO`; outra pessoa: 404; já
        respondido: 422. Invalida o token
  - [ ] `GET /api/pedidos-troca?papel=enviados|recebidos` (sem `papel`, os dois), só os do próprio usuário; mais
        recentes primeiro, paginado; coordenador, vice, padre e ADMIN **também** veem os pedidos das pastorais que
        gerenciam (`papel=pastoral`). `GET /api/pedidos-troca/{id}`: envolvido, coordenação da pastoral ou 404
  - [ ] Resposta (`PedidoTrocaResponseDTO`): `id`, `status`, `motivo` (só para solicitante, destinatário e
        coordenação), `expiraEm`, `alocacaoId`, `solicitante`/`destinatario` como `Ref` (id e nome), dados da
        celebração e da função. **Nunca** devolve o `token_hash`
- [ ] `ExpiracaoTrocasJob` (`service/troca`, `@ConditionalOnProperty servio.jobs.enabled`, a cada 10 minutos, mesmo
      padrão do `ExpiracaoConvitesJob`): pedido `ABERTO` com `expira_em` ≤ agora passa a `EXPIRADO` (`UPDATE`
      condicional, cada pedido na própria transação, `try/catch` por item), invalida o token e publica
      `TrocaExpiradaEvent` (só quem expirou publica). O aviso a quem pediu e à coordenação sai pelo cartão 2
- [ ] A **alocação do solicitante não muda** em nenhuma dessas transições (a troca só efetiva no cartão 3): quem pediu
      e ninguém aceitou continua escalado
- [ ] `AuditLog` `TROCA_SOLICITADA` e `TROCA_CANCELADA` (referência: id do pedido; detalhe: ids da alocação e da
      celebração, sem nomes nem motivo)

Cuidados com testes existentes:

- [ ] Não há testes nem front que usem `/api/pedidos-troca`: o contrato antigo pode sair inteiro. Acrescentar a rota
      nova em `AutorizacaoIT` (sem login 401, sem CSRF 403) e `MassAssignmentIT` (campo a mais no corpo é 400:
      `status`, `solicitanteId`, `paroquiaId`)
- [ ] `TenantIsolamentoIT`: pedido de outra paróquia dá 404, como o resto

Testes novos — `PedidoTrocaIT` e `PedidoTrocaServiceTest` (unitário, `Clock.fixed`):

- [ ] Pedido válido: 201, `ABERTO`, `expiraEm` = início − 24 horas, uma linha `PENDENTE` em `email_saida` com template
      `troca-solicitada` para o e-mail do destinatário, sem motivo nas variáveis e com `/troca#` no link; o banco só
      tem o hash (64 caracteres, diferente do token)
- [ ] Alocação de outra pessoa: 404; alocação `PENDENTE`, `RECUSADA` ou `SUBSTITUIDA`: 422; celebração a menos de 24
      horas ou já começada: 422
- [ ] Destinatário: ele mesmo (422), de outra pastoral (422), de outra paróquia (404), inativo (404)
- [ ] Segundo pedido aberto para a mesma alocação: 409; depois de cancelar ou expirar, um novo é aceito
- [ ] Cancelar: só o solicitante (o destinatário e terceiros: 404), só `ABERTO` (já cancelado: 422); o link antigo morre
- [ ] Duas chamadas simultâneas de cancelar (ou de cancelar e expirar): uma vence, a outra recebe 422, e o status final
      é um só
- [ ] `ExpiracaoTrocasJob` (relógio avançado até `expira_em`): `ABERTO → EXPIRADO`, token invalidado, rodar de novo não
      publica de novo; a alocação continua `ACEITA` do solicitante
- [ ] Listagens: solicitante vê o pedido em `enviados`, destinatário em `recebidos`, coordenação em `pastoral`;
      membro de outra pastoral e outra paróquia não veem
- [ ] Nenhum log contém o token

### Cartão 2 — Avisos do app (lido e não lido) e aviso à coordenação

Problema: todo aviso do sistema hoje é e-mail. O sininho e a tela de Avisos precisam de uma tabela de avisos dentro do
app, com lido e não lido, e a coordenação tem de ser avisada quando uma troca é aceita. Cartão de infraestrutura: o
cartão 3 (e o 1, para o aviso de expiração) o usam.

Ler: `service/notification/{EnvioEscalacao, NotificacaoListener}`, `service/escalacao/evento/*`,
`service/MeService`, `controller/MeController`, `model/dto/MeResponseDTO`, `repository/UsuarioPastoralRepository`
(`findUsuariosPorPapel`), `service/AuditLogService`, `model/TenantEntity`, migration `V14__email_saida.sql` (tabela de
apoio sem `TenantEntity`).

- [ ] `V23__aviso.sql`: tabela `aviso` — `id BIGSERIAL`, `paroquia_id BIGINT NOT NULL REFERENCES paroquia`,
      `usuario_id BIGINT NOT NULL REFERENCES usuario`, `tipo VARCHAR(40) NOT NULL`, `titulo VARCHAR(160) NOT NULL`,
      `mensagem VARCHAR(500) NOT NULL`, `referencia_tipo VARCHAR(30)`, `referencia_id BIGINT` (para o front abrir a
      tela certa: `PEDIDO_TROCA`, `ALOCACAO`, `CELEBRACAO`), `criado_em TIMESTAMP NOT NULL DEFAULT now()`,
      `lido_em TIMESTAMP`; índice `(usuario_id, lido_em, criado_em DESC)`. Sem `is_active`/`version`: não se edita
- [ ] Entidade `Aviso` (não é `TenantEntity`, como `EmailSaida`; a paróquia vem do usuário e é gravada junto),
      `AvisoRepository` (`contarNaoLidos(usuarioId)`, página por usuário, `marcarLido(id, usuarioId, agora)` com
      `UPDATE ... WHERE lido_em IS NULL`, `marcarTodosLidos(usuarioId, agora)`, `apagarAntesDe(limite)`)
- [ ] `AvisoService` (`service/aviso`): `avisar(usuario, tipo, titulo, mensagem, referenciaTipo, referenciaId)` e
      `avisarCoordenacao(pastoral, ...)` (as pessoas com papel `COORDENADOR`, mesma consulta de
      `EnvioEscalacao.avisarCoordenadores`). Grava **na transação de quem chama** (o aviso só existe se a ação
      comitar). Mensagens curtas e sem dado sensível: sem motivo do pedido, sem justificativa de recusa, sem e-mail
- [ ] `EnvioEscalacao.avisarCoordenadores` passa a gravar também o aviso do app (um só ponto: os avisos de convite
      recusado, expirado e sem elegíveis ganham o sininho sem mudar o e-mail). **Mudança de comportamento
      pequena e descrita aqui**: nenhum e-mail muda
- [ ] Rotas, todas `authenticated` (`/api/me/**` já é): `GET /api/me/avisos?naoLidos=true|false&page&size` (mais
      recentes primeiro, `size` padrão 20, máximo 50), `POST /api/me/avisos/{id}/lido` → 204 (aviso de outra pessoa:
      404; já lido: 204, idempotente), `POST /api/me/avisos/lidos` → 204 (marca todos). CSRF continua exigido
- [ ] `MeResponseDTO` ganha `avisosNaoLidos` (número); `GET /api/me` e `PUT /api/me` devolvem o mesmo campo
- [ ] Job de limpeza diário de madrugada (`servio.jobs.enabled`): apaga avisos **lidos** com mais de 90 dias e
      qualquer aviso com mais de 180 (`servio.aviso.retencao-lidos-dias`, `servio.aviso.retencao-dias`)
- [ ] Conta de demonstração (`SomenteLeituraFilter`): `POST` de "lido" entra na regra que já bloqueia escrita; o
      `GET` funciona

Cuidados com testes existentes:

- [ ] `NotificacaoIT`, `JobsIT`: os e-mails de recusa, expiração e sem elegíveis continuam iguais (verificações
      sobre o `Notificador`); acrescentar só a conferência da linha em `aviso`
- [ ] `MeIT`: o JSON ganha `avisosNaoLidos` (0); os asserts atuais valem
- [ ] `OpenApiIT`: acrescentar `/api/me/avisos`; rodar o OpenAPI (`OPENAPI_ENABLED=true`) para o front rodar
      `npm run gen:api`

Testes novos — `AvisoIT` e `AvisoServiceTest` (unitário):

- [ ] `avisar` grava uma linha para a pessoa certa na paróquia certa; `avisarCoordenacao` grava uma por coordenador da
      pastoral e nenhuma para vice, membro nem para coordenador de outra pastoral
- [ ] Ação que desfaz a transação não deixa aviso (o aviso entra na mesma transação)
- [ ] Convite recusado, expirado e sem elegíveis: continuam mandando o e-mail **e** criam o aviso para os coordenadores
- [ ] `GET /api/me/avisos`: só os do próprio usuário, mais recentes primeiro, filtro `naoLidos`, paginação; de outra
      pessoa ou de outra paróquia: nunca aparece
- [ ] Marcar como lido: baixa `avisosNaoLidos` em `/api/me`; de outra pessoa 404; repetir é 204; marcar todos zera
- [ ] Limpeza: lido antigo e aviso antigo somem, não lido recente fica

### Cartão 3 — Aceitar ou recusar a troca, com revalidação das regras

Problema: nada muda a escala hoje. Decisão: quem recebe o pedido **aceita ou recusa**; ao aceitar, as regras são
**revalidadas** para o destinatário no momento da resposta (o estado da escala pode ter mudado desde o pedido), e a
escala **muda na hora**, sem exigir aprovação da coordenação, que é **avisada** (decisão do "pronto quando"; ver
Pendente/decisões). Empilhar nos cartões 1 e 2.

Ler: `service/PedidoTrocaService` (cartão 1), `service/escalacao/{EscalacaoService (substituir, novaAlocacao,
exigirNaoAlocado, exigirPosicaoLivre, travar), ElegibilidadeService, ConviteService, ConfiguracaoPastoralService}`,
`repository/{AlocacaoRepository, VagaRepository (findByIdParaEscalarComLock)}`, `model/{Alocacao, Vaga}`,
`model/enumerated/OrigemAlocacao`, `service/escalacao/evento/AlocacaoSubstituidaEvent`,
`service/notification/{NotificacaoListener, EnvioEscalacao}`, `service/AvisoService` (cartão 2), templates
`substituicao.*` e `aviso-coordenador.*`, testes `pastoral/{EscalacaoManualIT, SorteioIT}`.

- [ ] `OrigemAlocacao` ganha `TROCA` (coluna `varchar(20)` sem `CHECK`: sem migration). `AlocacaoResponseDTO` e
      `EscalaPessoalDTO` já devolvem a origem; o front mostra "por troca"
- [ ] `POST /api/pedidos-troca/{id}/aceitar` → **200** com o pedido `ACEITO`, e `POST .../recusar` → **200** com o
      pedido `RECUSADO` (corpo opcional `{ justificativa? }` ≤ 500 caracteres, aparada, vazio vira `null`, igual ao
      convite). **Só o destinatário** logado; solicitante e terceiros: 404. Pedido não `ABERTO`: 422
- [ ] Aceitar, numa transação, na ordem:
  - [ ] trava a vaga (`findByIdParaEscalarComLock`); recarrega o pedido e confere que continua `ABERTO`, que a
        alocação do solicitante continua `ACEITA` e ativa e que a celebração ainda não começou (senão 422 "Este
        serviço não pode mais ser trocado")
  - [ ] **revalida** o destinatário: `ElegibilidadeService.impedimentos(destinatario, vaga, alocacaoDoSolicitante)`
        mais `exigirNaoAlocado` e `exigirPosicaoLivre`. Com impedimento, **nada muda** e a resposta é **422** com os
        motivos juntos ("Você não pode assumir este serviço: ..."); o pedido continua `ABERTO` para o destinatário
        poder recusar. Sem `forcar`: troca entre membros não fura regra (ver Pendente/decisões)
  - [ ] `UPDATE` condicional do pedido para `ACEITO` (`respondido_em`, token invalidado); 0 linhas = 422
  - [ ] a alocação do solicitante vira `SUBSTITUIDA` (`substituidaEm`, `invalidarTokens()`); nasce a do destinatário
        **já `ACEITA`** (`origem = TROCA`, `respondidoEm` = agora, `escaladoPor` = o solicitante, sem token e sem
        prazo de resposta), na mesma vaga
  - [ ] publica `TrocaAceitaEvent`
- [ ] Depois do commit (`NotificacaoListener`, padrão dos outros eventos, deduplicado por pedido):
  - [ ] e-mail e aviso do app para o **solicitante** ("Fulano aceitou assumir o seu serviço") e para o
        **destinatário** (confirmação, com os dados do serviço)
  - [ ] aviso para a **coordenação** da pastoral (e-mail `aviso-coordenador` e aviso do app, sem o motivo do
        pedido): "A troca foi feita: X no lugar de Y em <celebração>, função <função>"
- [ ] Recusar: pedido `RECUSADO` (`respondido_em`, token invalidado), alocação do solicitante **intacta**; e-mail e
      aviso do app para o solicitante (sem a justificativa no e-mail; no app, só para ele). A coordenação não é
      avisada da recusa (ver Pendente/decisões)
- [ ] `TrocaExpiradaEvent` (cartão 1): e-mail e aviso do app para o solicitante ("ninguém assumiu, o serviço continua
      seu") e aviso para a coordenação. É a segunda situação em que a coordenação precisa agir
- [ ] `AuditLog` `TROCA_ACEITA`, `TROCA_RECUSADA` e `TROCA_EXPIRADA` (ids do pedido, da alocação antiga, da nova e da
      celebração, sem nomes nem justificativa)
- [ ] A troca **não** gera `AlteracaoPendente` (esse fluxo é do vice que escala por cima da coordenação)
- [ ] `LembretesJob` e o lembrete de serviço seguem normais: a alocação do destinatário (`ACEITA`) entra nos lembretes
      de serviço; a do solicitante (`SUBSTITUIDA`) sai

Cuidados com testes existentes:

- [ ] `EscalacaoManualIT`, `SorteioIT`, `AlocacaoRegrasIT`: não mudam (a troca não toca `EscalacaoService.substituir`;
      se for preciso extrair um método comum, o comportamento das rotas atuais fica idêntico e esses testes provam)
- [ ] `JobsIT`: o lembrete de serviço ignora alocação `SUBSTITUIDA` (já ignora) e inclui a nova (`ACEITA`)

Testes novos — `TrocaRespostaIT`:

- [ ] Caminho feliz: pedido `ABERTO`, destinatário aceita: 200 `ACEITO`; a alocação do solicitante vira `SUBSTITUIDA`;
      existe uma `ACEITA` do destinatário na mesma vaga com `origem = TROCA` e `respondidoEm`; `GET /api/me/escalas`
      mostra a vaga para o destinatário e não mais para o solicitante
- [ ] Os avisos: solicitante, destinatário e **coordenador da pastoral** recebem aviso do app; e-mails na fila para
      solicitante e destinatário (aviso à coordenação por `aviso-coordenador`); nenhum contém o motivo; vice e
      coordenador de outra pastoral não recebem
- [ ] Revalidação: depois do pedido, o destinatário ganha outra vaga na mesma celebração (regra `UmaVagaPorCelebracao`)
      ou indisponibilidade: aceitar dá 422 com o motivo, **nada** muda, o pedido segue `ABERTO` e ele ainda pode
      recusar
- [ ] Solicitante, terceiro e outra paróquia tentando aceitar ou recusar: 404; pedido já respondido, cancelado ou
      expirado: 422
- [ ] Duas respostas ao mesmo tempo (aceitar e aceitar; aceitar e cancelar do solicitante): só uma vence, a escala
      termina com uma única alocação ocupante da vaga para a pessoa certa (sem duplicar, sem vaga a mais)
- [ ] Alocação do solicitante virou `RECUSADA`, `SUBSTITUIDA` ou a celebração já começou entre o pedido e a resposta:
      422 "Este serviço não pode mais ser trocado"
- [ ] Recusar: pedido `RECUSADO`, alocação intacta, solicitante avisado, coordenação **não** avisada
- [ ] Expiração (job do cartão 1 com relógio avançado): solicitante e coordenação avisados, alocação intacta
- [ ] Aceitar a troca e logo depois pedir de novo (agora a partir da nova alocação): permitido, vale a regra das 24 horas
- [ ] Nenhum log contém o motivo nem a justificativa

### Cartão 4 — Página pública para aceitar a troca pelo link do e-mail

Problema: o destinatário precisa responder pelo celular, sem login, como no convite (decidido em 14/10: sim, igual à
do convite). O token do pedido (cartão 1) é a credencial. Empilhar nos cartões 1 e 3.

Ler: `controller/ConfirmacaoController`, `service/escalacao/{ConfirmacaoPublicaService, LimiteConfirmacoes,
RespostaConviteService, TokenConvite}`, `model/dto/Confirmacao*DTO`, `service/PedidoTrocaService` (cartão 3),
`config/SecurityConfig` (`/api/confirmacoes/**` já é `permitAll`), testes `pastoral/{ConvitePublicoIT,
ConviteRespostaIT}`, `service/conta/LimiteContaPublica` (sprint 2) para a decisão do balde de limite.

- [ ] `POST /api/confirmacoes/troca/detalhes` `{ token }` → `{ primeiroNomeSolicitante, primeiroNomeDestinatario,
      celebracaoTitulo, data, hora, funcao, prazo, status }`. Não altera nada. Só primeiro nome: nada de sobrenome,
      e-mail, telefone nem motivo
- [ ] `POST /api/confirmacoes/troca/responder` `{ token, aceitar, justificativa? }` → **200** `{ status }`
      (`ACEITO` ou `RECUSADO`). Chama a **mesma regra** do cartão 3 (`aceitar`/`recusar` do
      `PedidoTrocaService`), com o destinatário vindo do token em vez do usuário logado. Impedimento nas regras: 422
      com os motivos (a pessoa precisa ver por quê); o pedido continua `ABERTO`
- [ ] Erros iguais aos do convite: token inexistente, já usado, cancelado, de pedido inativo ou de outro tipo de token
      dão **a mesma 404** ("Pedido de troca não encontrado ou já respondido"); `expira_em` vencido dá **410**
      (e, na resposta, o pedido é gravado como `EXPIRADO` se o job ainda não o fez, com `noRollbackFor`, como no
      convite); justificativa acima de 500: 422
- [ ] `LimiteConfirmacoes` (30 por minuto por IP), o **mesmo balde** do convite: são a mesma família de página
      pública, abertas a partir do mesmo e-mail/celular (ver Pendente/decisões)
- [ ] A rota autenticada e a pública convivem: quem responde pelo app e depois abre o link antigo recebe a 404
      (token invalidado)
- [ ] O token nunca aparece em log, em resposta nem em `AuditLog`; o link só existe no e-mail (cartão 1)
- [ ] CSRF continua ligado (o front chama `GET /api/auth/csrf` antes, como no convite)

Cuidados com testes existentes:

- [ ] `ConvitePublicoIT`, `ConviteRespostaIT`: não mudam (rotas e balde do convite intactos)
- [ ] `AutorizacaoIT`/`CsrfIT`: acrescentar as rotas novas (sem CSRF 403; `permitAll` já cobre `/api/confirmacoes/**`)

Testes novos — `TrocaPublicaIT`:

- [ ] Fluxo completo sem login: pedido criado (token pego do e-mail enfileirado, como `AtivacaoContaIT`), `detalhes`
      mostra o serviço e os primeiros nomes, `responder` com `aceitar: true` troca a escala e avisa a coordenação
      (mesmos asserts do cartão 3)
- [ ] `responder` com `aceitar: false`: `RECUSADO`, escala intacta
- [ ] Mesmo token de novo: 404; token inexistente, de convite (outro tipo de link) e de pedido cancelado: a mesma 404
- [ ] Prazo vencido (relógio +24 horas antes do evento): 410 e o pedido fica `EXPIRADO`; a alocação do solicitante continua
- [ ] Regra impedindo na hora de aceitar: 422 com o motivo, pedido `ABERTO`, token ainda válido
- [ ] 31ª chamada no mesmo minuto: 429
- [ ] Resposta e `detalhes` não contêm sobrenome, e-mail, motivo nem hash do token
- [ ] Nenhum log contém o token

### Cartão 5 — `GET /api/me/inicio`

Problema: a tela Início do membro precisa, numa chamada só, do que a pessoa tem para fazer e para saber hoje. Hoje o
front teria de juntar `GET /api/me/escalas`, o painel de cada pastoral, os convites, as trocas e os avisos. Decisão:
uma rota só, só leitura, sem N+1. A Início da **gestão** (coordenação) é da sprint 6.

Ler: `controller/MeController`, `service/{MeService, leitura/LeituraEscalaService (minhasEscalas)}`,
`repository/AlocacaoRepository (escalasDoUsuario, contarDoUsuarioNaPastoral)`, `service/escalacao/PainelService`
(`contarPendencias`), `service/PedidoTrocaService` (cartão 1), `service/AvisoService` (cartão 2), `model/dto/EscalaDTOs`,
teste `pastoral/LeituraIT` (conta consultas SQL: o padrão para provar que não há N+1).

- [ ] `GET /api/me/inicio` → um DTO só, para qualquer pessoa logada (padre e ADMIN recebem as seções vazias que não se
      aplicam):
  - [ ] `proximasEscalas`: as próximas escalas **confirmadas** (`ACEITA`) da pessoa nos próximos 30 dias, no máximo 5
        (`EscalaPessoalDTO`, do dia mais próximo ao mais distante), e `proximaEscala` = a primeira ou `null`
  - [ ] `convitesParaResponder`: convites `PENDENTE` com prazo no futuro, ordenados pelo prazo mais curto (a tela mostra
        o que vence primeiro), cada um com `dataLimiteResposta`
  - [ ] `trocasRecebidas` (pedidos `ABERTO` em que a pessoa é destinatária, com `expiraEm`) e `trocasEnviadas`
        (pedidos `ABERTO` que ela fez)
  - [ ] `avisosNaoLidos` (número) e os 3 avisos mais recentes não lidos
  - [ ] `pastorais[]`: id, nome, papel e `pendencias` (o que `GET /api/me` já devolve), para o front não chamar as duas
        rotas
- [ ] Só dados **do próprio usuário** (e a contagem de pendências que `/api/me` já mostra). Nada de dado de outra pessoa
      além do que a escala já mostra a quem é da pastoral (primeiro nome do solicitante/destinatário da troca)
- [ ] Sem N+1: o número de consultas SQL não cresce com o número de escalas, convites ou avisos (mesmo teste de
      `LeituraIT`)
- [ ] Sem alteração: nenhuma escrita, nenhum aviso é marcado como lido por abrir a Início

Cuidados com testes existentes:

- [ ] `MeIT`, `PainelIT`: sem mudança (a rota é nova; o painel só é reaproveitado)
- [ ] `OpenApiIT`: acrescentar `/api/me/inicio`; rodar o OpenAPI para o front rodar `npm run gen:api`

Testes novos — `InicioIT`:

- [ ] Membro com 2 escalas aceitas, 1 convite pendente, 1 troca recebida e 2 avisos: cada seção na ordem certa, só os
      dados dele; membro de outra pastoral e de outra paróquia não aparecem
- [ ] Escala depois de 30 dias, convite vencido, pedido `RECUSADO`/`EXPIRADO` e aviso lido: fora das seções
- [ ] 3 e 30 escalas/convites/avisos: **mesmo número de consultas SQL**
- [ ] Padre e ADMIN: 200 com seções vazias; sem login: 401
- [ ] Abrir a Início não muda `avisosNaoLidos`

## Contrato combinado (rotas novas; ver `sprint-0.md`, seção 5)

> O "Mapa de endpoints" do front ainda não estava disponível quando este documento foi escrito: **nomes e formatos
> abaixo são a proposta do back-end** e precisam ser confirmados com o colaborador antes do cartão 1.

- [ ] `POST /api/pedidos-troca` `{ alocacaoId, destinatarioId, motivo? }` → 201; `POST /api/pedidos-troca/{id}/cancelar`
      → 204; `POST /api/pedidos-troca/{id}/aceitar` e `/recusar` `{ justificativa? }` → 200;
      `GET /api/pedidos-troca?papel=enviados|recebidos|pastoral`. `PUT`/`DELETE /api/pedidos-troca/{id}` **deixam de
      existir**. Estados: `ABERTO`, `ACEITO`, `RECUSADO`, `CANCELADO`, `EXPIRADO`
- [ ] Erros: 404 (não é seu / não existe), 409 (já há pedido aberto), 422 (24 horas, regras, já respondido, serviço
      que mudou)
- [ ] Públicas (sem login, com CSRF): `POST /api/confirmacoes/troca/detalhes` e `POST /api/confirmacoes/troca/responder`;
      link no e-mail: `/troca#token`; 404 token inválido/usado, 410 vencido, 422 regra ou justificativa, 429 limite
- [ ] `GET /api/me/avisos?naoLidos&page&size`, `POST /api/me/avisos/{id}/lido`, `POST /api/me/avisos/lidos`;
      `GET /api/me` e `PUT /api/me`: `avisosNaoLidos` novo
- [ ] `GET /api/me/inicio` (seções acima)
- [ ] `AlocacaoResponseDTO.origem` e `EscalaPessoalDTO.origem`: novo valor `TROCA`
- [ ] Mensagens de erro que o front vai mostrar (faltam menos de 24 horas, já existe pedido aberto, a pessoa não é da
      pastoral, você não pode assumir este serviço + motivos, este serviço não pode mais ser trocado, pedido já
      respondido, link inválido, link vencido, muitas tentativas)

## Fechamento da sprint

- [ ] Atualizar `Features/features-implementadas.md` e `Features/erros-conhecidos.md` (o item 5, "`PedidoTroca` sem
      regras", sai da lista de abertos) com o que mudou
- [ ] Atualizar `Features/checklist-seguranca.md` (rota pública nova da troca, token de troca só em hash e no fragmento
      da URL, avisos do app só do próprio usuário) e o `runbook-piloto.md` (como a coordenação confere uma troca, o que
      fazer se o e-mail do pedido não chegou: pedir pelo app, que mostra os pedidos recebidos)
- [ ] Resumo por cartão: o que mudou, testes novos, e qualquer coisa que ficou pendente ou decidida diferente
      do planejado (com o motivo)
- [ ] Conferir o "Pronto quando": no servidor de teste, uma pessoa escalada pede a troca a outra da mesma pastoral, a
      outra aceita pelo link do e-mail no celular, a escala muda e o coordenador vê o aviso (sininho e e-mail)
- [ ] Conferir a expiração: pedido sem resposta vence 24 horas antes da missa, quem pediu continua escalado e é avisado

## Pendente/decisões

Dúvidas reais deste planejamento. Nenhuma foi decidida por palpite: a coluna "padrão proposto" é o que o código
faz **se ninguém decidir diferente**, para não travar o cartão.

| Dúvida | Padrão proposto | Cartão |
|---|---|---|
| A troca aceita muda a escala **na hora** ou espera o coordenador aprovar? | Muda na hora e avisa a coordenação (é o texto do "pronto quando"). Aprovação prévia é outra tela e outro estado (`AGUARDANDO_COORDENACAO`) que não existe no escopo; se quiserem, vira cartão da sprint 6 | 3 |
| Pedido para "quem puder" (sem destinatário) | Não nesta sprint: o destinatário é obrigatório e da mesma pastoral. Pedido aberto exige lista de elegíveis, corrida entre quem aceita e outro aviso; o campo continua nulável na tabela só por causa dos registros antigos | 1 |
| Destinatário que não passa nas regras: a coordenação pode forçar? | Não: a troca entre membros respeita as regras ativas da pastoral (sem `forcar`). Quem precisa furar regra pede à coordenação, que usa `substituir` com `forcar` como hoje | 3 |
| Quem pode pedir troca: só alocação `ACEITA` ou também convite `PENDENTE`? | Só `ACEITA`. Quem tem convite pendente responde o convite (recusar já devolve a vaga à escala) | 1 |
| Pedido a menos de 24 horas do evento | 422 "fale com a coordenação"; a decisão de 14/10 é que o pedido **expira** 24 horas antes, então abrir um que nasce vencido não faz sentido | 1 |
| O que acontece com o serviço quando o pedido expira sem resposta | Continua com quem pediu (a alocação não muda) e a coordenação é avisada. Quem não puder ir fala com a coordenação | 1, 3 |
| Motivo do pedido (texto livre): quem vê | Solicitante, destinatário (no app) e coordenação da pastoral; **nunca** em e-mail nem em log nem em aviso. Opcional, até 500 caracteres | 1 |
| Coordenação é avisada quando a troca é **recusada**? | Não (nada mudou na escala). Só quando é aceita (a escala mudou) e quando expira (alguém precisa agir) | 3 |
| Aviso à coordenação por e-mail, no app ou os dois | Os dois: e-mail pelo `aviso-coordenador` já existente e aviso no sininho. Os avisos antigos (recusa, expiração, sem elegíveis) também ganham o sininho, sem mudar o e-mail | 2, 3 |
| Retenção dos avisos do app | Lidos: 90 dias; qualquer aviso: 180 dias. Sem apagar por pedido da pessoa (fora do escopo) | 2 |
| Balde de limite da página pública da troca | O mesmo do convite (`LimiteConfirmacoes`, 30 por minuto por IP): é a mesma família de link aberto pelo celular. Separar (como se fez com as páginas de conta na sprint 2) só se o piloto mostrar disputa por cota | 4 |
| A página pública da troca mostra o motivo do pedido | Não: texto livre do solicitante não vai para uma página sem login. O destinatário vê o motivo dentro do app | 4 |
| Aceitar pela página pública exige confirmar a identidade (login) | Não: o token do e-mail é a credencial, como no convite. Quem pode ler o e-mail do destinatário já pode responder o convite dele | 4 |
| Troca entre pastorais diferentes (ex.: quem é da Pascom e do ECC) | Fora do escopo: troca só dentro da pastoral da vaga | 1 |
| Trocar uma alocação que já é fruto de troca (`origem = TROCA`) | Permitido, com a mesma regra das 24 horas | 3 |
| Textos dos e-mails de troca solicitada, aceita, recusada e expirada | Rascunhos simples; trocar só os `.html`/`.txt` depois, sem mexer em Java (mesmo padrão dos e-mails da sprint 2) | 1, 3 |
| `GET /api/me/inicio` para quem coordena | Devolve o mesmo para todos, mais `pastorais[].pendencias`; a Início da gestão (painel, pendências detalhadas) é da sprint 6 | 5 |
| Janela e tamanho de "próximas escalas" na Início | 30 dias e no máximo 5; o front usa `GET /api/me/escalas` para a lista completa | 5 |
| Nomes das rotas e dos campos (sem o "Mapa de endpoints") | Proposta no "Contrato combinado"; confirmar com o colaborador antes de começar o cartão 1 | 1 a 5 |
| Capacidade: a sprint 5 cabe junto com a correção dos erros da sprint 4 | Se faltar tempo, cortar o cartão 5 (`/api/me/inicio`), que o front consegue montar com `GET /api/me`, `/api/me/escalas` e `/api/me/avisos` | 5 |

## Fora desta sprint

- [ ] Telas Início do membro, Escalas, Pedir troca, Disponibilidade, sininho e tela de Avisos: trabalho do colaborador
- [ ] Troca "para quem puder", troca entre pastorais, aprovação prévia da coordenação, troca com `forcar`, notificação push,
      apagar avisos por pedido da pessoa
- [ ] Início da gestão (painel, pendências detalhadas) e auditoria completa das ações de escala: sprint 6

Ver também: `sprint-4.md`, `sprint-2.md`, `sprint-1.md`, `sprint-0.md`, `features-futuras.md`.
