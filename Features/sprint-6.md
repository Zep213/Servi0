# Servio — Sprint 6

**Período:** 18/01 a 31/01/2027 · **Back-end:** Zep · **Front-end:** colaborador

Gestão e financeiro. **Pronto quando:** todo lançamento e toda alteração de escala aparecem na auditoria.

Stack: Spring Boot 4.1.1, Java 25, Maven wrapper, JPA/Hibernate, Lombok, MapStruct, Flyway, PostgreSQL 17,
Spring Session JDBC, Spring Security com `formLogin`, Thymeleaf para e-mails, Testcontainers nos ITs (`*IT`).

Detalhado a partir de `features-futuras.md` (seção "Sprint 6") e do código que já está na `main`; ver também
`sprint-2.md`, `sprint-3.md` e `sprint-0.md`. Escopo desta sprint é **só back-end**: Início da gestão, Mais,
Financeiro, Reuniões e Pedir reunião são telas do colaborador (front).

> **Risco do cronograma: com capacidade menor que a prevista, esta sprint sai do MVP e vai para depois do piloto**
> (a escala é o que importa para a Pascom). Para decidir isso sem refazer o plano, os cartões estão em ordem de
> **corte**: os cartões 1, 2 e 4 entregam o "pronto quando" (toda alteração de escala e todo lançamento auditados) e
> são o mínimo; os cartões 3 e 5 podem escorregar para depois do piloto sem quebrar nada. Ver "Se faltar capacidade".

## O que já existe (e o que falta)

- **Auditoria hoje cobre quase nada.** `AuditLogService.registrar(tipoEvento, referenciaId, usuario, paroquiaId,
  detalhe)` existe e a tabela `audit_log` tem `tipo_evento`, `referencia_id`, `usuario_id`, `paroquia_id` e `detalhe`
  (`VARCHAR(255)`). Quem grava hoje: login (sucesso e falha, `AuditoriaLoginListener`), o ADMIN assumindo/saindo de
  paróquia e agindo nela (`PlataformaService`, `AuditoriaAdminAssumidoFilter`), e os eventos de conta da sprint 2
  (`MEMBRO_CADASTRADO`, `CONTA_ATIVADA`, `TELEFONE_ALTERADO`; a sprint 3 acrescenta `HABILIDADE_ALTERADA`). **Nenhuma
  ação de escala, de papel/perfil ou de dinheiro é auditada.**
- `GET /api/audit-logs` (`AuditLogController`) devolve a lista **inteira**, sem paginação, sem filtro (ADMIN: todas as
  paróquias; PADRE: a própria), e só PADRE/ADMIN leem (`SecurityConfig`). A tabela não tem `pastoral_id`, então não dá
  para a coordenação ver a auditoria da própria pastoral.
- **Financeiro** (`LancamentoFinanceiroService`, `lancamento_financeiro` na `V7`): `GET`/`POST`
  `/api/pastorais/{id}/financeiro`, `GET .../saldo`, `DELETE .../{id}`, e o resumo do padre em `/api/financeiro/resumo`.
  Não há `PUT`: nunca se edita. Mas o `DELETE` é uma **exclusão lógica** (`is_active = false`): o lançamento some da
  lista, do saldo e do resumo **sem deixar rastro** (nem motivo, nem quem). É o oposto do que um caixa precisa.
  Leitura: tesoureiro/coordenador da pastoral, padre e ADMIN; escrita: só tesoureiro da pastoral e ADMIN.
- **Reunião** (`reuniao` na `V8`, `ReuniaoService`): o coordenador marca direto (`POST .../reunioes`) e qualquer outro
  papel "solicita" em `POST .../reunioes/solicitar`, que **só manda um e-mail** de texto aos coordenadores pela
  `FilaEmail`. O pedido não é gravado: não há lista, status nem resposta, e o coordenador não vê os pedidos pendentes
  no app.

## Pré-requisitos (o que já precisa estar na `main`)

- [ ] Sprints 1 a 5 mergeadas. Esta sprint usa, de lá: `FilaEmail` (sprint 1), a auditoria de conta e o
      `AuditLogService` com `registrar` (sprint 2), `HABILIDADE_ALTERADA` e os DTOs de membros (sprint 3), e as
      mudanças de escala/agenda da sprint 4 (cujos pontos de entrada também precisam ser auditados, ver cartão 2)
- [ ] **Numeração de migrations:** a próxima livre **depois da sprint 5** (as sprints 1 a 4 usam até `V21`, a sprint 5
      pega as seguintes). Conferir `ls src/main/resources/db/migration` antes de nomear. Abaixo, as migrations são
      chamadas de **"migration A/B/C"** e recebem o número real na hora de codar
- [ ] Decisão sobre quem lê a auditoria (cartão 1) e sobre o estorno (cartão 4): ver Pendente/decisões

## Regras de trabalho

- [ ] Antes de codar cada cartão: ler as classes citadas nele e mostrar o plano antes de começar
- [ ] Um branch por cartão, a partir do `main`: `feat/s6-auditoria-base`, `feat/s6-auditoria-escala`,
      `feat/s6-auditoria-membros`, `feat/s6-financeiro-estorno`, `feat/s6-pedido-reuniao`. Os cartões 2 e 3
      **dependem do 1** (usam o helper e o `pastoral_id` do log) e empilham nele; o cartão 4 também (audita o estorno)
- [ ] Commits pequenos, mensagem em português
- [ ] Migrations novas a partir da **próxima livre depois da sprint 5** (nunca editar migration que já existe)
- [ ] `./mvnw verify` ao fim de cada cartão (precisa do Docker ligado); não passar para o próximo com teste vermelho
- [ ] Não mudar comportamento fora do que está descrito em cada cartão; o que parecer errado no caminho vai pra
      "Pendente/decisões" no fechamento, não é corrigido de lado
- [ ] **Auditoria não é log:** o `detalhe` guarda ids e números, nunca e-mail, telefone, token, senha nem o texto de um
      motivo longo (cabe 255). Mensagens de erro e comentários em português, no estilo do código atual
- [ ] **Gravar a auditoria na mesma transação da ação**: se a ação desfaz, o registro desfaz junto (nada de log de
      coisa que não aconteceu); se o registro falha, a ação falha (auditoria que pode faltar não é auditoria)

## Escopo (5 cartões, nesta ordem)

### Cartão 1 — Base da auditoria: quem fez, em qual pastoral, e uma consulta que sirva

Problema: `audit_log` não guarda a pastoral da ação, `AuditLogService.registrar` exige uma entidade `Usuario` (cada
chamador precisa buscá-la), e `GET /api/audit-logs` devolve tudo de uma vez. Sem isso os cartões 2 a 4 repetiriam
código e a coordenação não teria como ler o que importa.

Ler: `service/AuditLogService`, `model/AuditLog`, `repository/AuditLogRepository`, `controller/AuditLogController`,
`mapper/AuditLogMapper`, `model/dto/AuditLogResponseDTO`, `config/SecurityConfig` (`/api/audit-logs/**`),
`security/{AuditoriaLoginListener, AuditoriaAdminAssumidoFilter, PastoraisPermissao, UsuarioLogado}`,
`service/PlataformaService`, `service/conta/CadastroMembroService` (um chamador atual), migrations `V1`, `V2`
(`paroquia_id` no log) e `V10_1` (`detalhe`).

- [ ] **Migration A:** `audit_log` ganha `pastoral_id BIGINT NULL REFERENCES pastoral` e índices
      `(paroquia_id, criado_em DESC)` e `(pastoral_id, criado_em DESC) WHERE pastoral_id IS NOT NULL` (conferir o nome
      da coluna de data em `BaseEntity`/`V1` antes de nomear). Registros antigos ficam com `pastoral_id` nulo
- [ ] `AuditLogService.registrar` ganha uma sobrecarga para **quem está logado**:
      `registrar(tipoEvento, referenciaId, pastoralId, detalhe)`, que lê usuário e paróquia efetivos do
      `UsuarioLogado` (inclusive a paróquia assumida pelo ADMIN) e grava o `usuario` como referência
      (`UsuarioRepository.getReferenceById`, sem consulta). As sobrecargas atuais continuam, para os chamadores de hoje
- [ ] Constantes dos tipos de evento num lugar só (`model/enumerated/TipoAuditoria` ou constantes em
      `AuditLogService`), com **um nome por ação**: `ALOCACAO_CRIADA`, `ALOCACAO_ALTERADA`, `ALOCACAO_REMOVIDA`,
      `ESCALA_SORTEADA`, `LANCAMENTO_CRIADO`, `LANCAMENTO_ESTORNADO`, `REUNIAO_MARCADA` etc. (a lista completa nos
      cartões 2 a 5). Os tipos que já existem mantêm o nome (quem lê relatórios não pode ver o nome mudar)
- [ ] **`GET /api/audit-logs` com paginação e filtros**: `Page` (padrão 50, ordem do mais novo para o mais antigo) e
      filtros opcionais `tipo` (exato ou prefixo, ex. `ALOCACAO_`), `pastoralId`, `usuarioId`, `de`/`ate` (datas).
      **Quebra de contrato**: hoje devolve array; passar a `Page` muda o formato (o front não usa a rota ainda, ver
      Pendente/decisões). Parâmetro inválido: 400
- [ ] **Quem lê:** PADRE e ADMIN, como hoje (ADMIN em todas as paróquias, PADRE na própria), **e o coordenador da
      pastoral**, só os registros com `pastoral_id` das pastorais que coordena. A regra fina fica no service
      (`PastoraisPermissao`), e o `SecurityConfig` troca `hasAnyRole("ADMIN","PADRE")` por `authenticated()` para
      `GET /api/audit-logs/**` (nada de escrita pela API: `POST`/`PUT`/`DELETE` continuam sem rota). Registro de
      login (sem pastoral) continua só para PADRE/ADMIN
- [ ] `AuditLogResponseDTO` ganha `pastoralId` e o **nome de quem fez** (`usuarioNome`, sem e-mail); a consulta traz o
      usuário em lote (sem N+1)
- [ ] Retenção: nesta sprint **nada é apagado** (auditoria não expira); ver Pendente/decisões

Cuidados com testes existentes:

- [ ] Testes que leem `/api/audit-logs` (`grep -rn "audit-logs" src/test`) esperam array: acertar para `Page`
- [ ] Testes que usam `auditLogRepository.findAll()` continuam valendo (a tabela só ganha uma coluna nula)
- [ ] `AutorizacaoIT`: a rota deixa de ser "só PADRE/ADMIN" no `SecurityConfig`; atualizar os casos para o novo gate
      (membro comum e coordenador de **outra** pastoral veem lista vazia, não 403: o filtro é por registro)

Testes novos — `AuditoriaConsultaIT`:

- [ ] Registro gravado pelo usuário logado traz `usuarioId`, `paroquiaId` e `pastoralId`; com o ADMIN assumindo uma
      paróquia, grava a paróquia assumida
- [ ] Paginação e ordem (mais novo primeiro); filtros por `tipo` (exato e prefixo), `pastoralId`, `usuarioId` e período
- [ ] Coordenador só vê os registros das suas pastorais; padre vê os da paróquia; ADMIN vê tudo; membro comum vê vazio
- [ ] Paróquia B não enxerga registro da paróquia A por nenhum filtro
- [ ] Parâmetro inválido: 400; a resposta nunca traz e-mail nem telefone

### Cartão 2 — Auditoria das ações de escala

Problema: sortear, escalar, substituir, remover alguém da vaga, mexer em vaga/celebração e responder convite não deixam
rastro. "Quem tirou a Maria do domingo?" não tem resposta.

Ler: `service/escalacao/{EscalacaoService, RespostaConviteService, ConfirmacaoPublicaService, ConviteService}`,
`service/{AlocacaoService, AlteracaoPendenteService, VagaService, CelebracaoService, ModeloVagaService}`,
`service/escalacao/PastoralConfigService` (`salvar`), `controller/{AlocacaoController, EscalacaoController,
VagaController, CelebracaoController}`, `service/escalacao/job/ExpiracaoConvitesJob`, e (da sprint 4) os pontos de
entrada novos de geração de missa e de evento. Os pontos de escrita **de hoje** estão em `AlocacaoService`
(`criar`/`atualizar`/`desativar`, herdados de `CrudService`) e em `EscalacaoService`.

- [ ] Gravar com o helper do cartão 1, **na mesma transação**, estes eventos (`referenciaId` = id da alocação, vaga ou
      celebração; `pastoralId` da função da vaga; `detalhe` com ids e contagens):

  | Ação | Tipo | Onde |
  |---|---|---|
  | Escalar à mão (`POST /api/vagas/{id}/escalar`, `POST /api/alocacoes`) | `ALOCACAO_CRIADA` (`detalhe`: vaga, pessoa, origem, se forçou) | `EscalacaoService.escalar`, `AlocacaoService.criar` |
  | Trocar a pessoa/vaga de uma alocação (`PUT /api/alocacoes/{id}`) | `ALOCACAO_ALTERADA` | `AlocacaoService.atualizar` |
  | Remover (`DELETE /api/alocacoes/{id}`) | `ALOCACAO_REMOVIDA` | `AlocacaoService.desativar` |
  | Sortear (celebração ou vaga) | `ESCALA_SORTEADA` (`detalhe`: vagas, convidados, incompletas; **não** um registro por convidado) | `EscalacaoService.sortear*` |
  | Substituir | `ALOCACAO_SUBSTITUIDA` (`detalhe`: antiga, nova) | `EscalacaoService.substituir` |
  | Reenviar convite | `CONVITE_REENVIADO` | `EscalacaoService.reenviarConvite` |
  | Responder (aceitou, recusou, expirou) | `CONVITE_ACEITO`, `CONVITE_RECUSADO`, `CONVITE_EXPIRADO` | `RespostaConviteService.aplicar`, `ExpiracaoConvitesJob` |
  | Confirmar/desfazer alteração do vice | `ALTERACAO_CONFIRMADA`, `ALTERACAO_DESFEITA` | `AlteracaoPendenteService` |
  | Vaga e celebração: criar, mudar quantidade/horário, excluir | `VAGA_CRIADA`, `VAGA_ALTERADA`, `VAGA_REMOVIDA`, `CELEBRACAO_*` | `VagaService`, `CelebracaoService` |
  | Regras e prazos da pastoral | `CONFIG_PASTORAL_ALTERADA` (`detalhe`: chave, ligada/desligada) | `PastoralConfigService.salvar` |

- [ ] **Quem é o autor quando não há usuário logado:** convite respondido pelo link público (autor = a pessoa
      convidada, `usuarioId` da alocação) e expiração pelo job (autor nulo, tipo `CONVITE_EXPIRADO`, `detalhe`
      "prazo vencido"). O job grava no mesmo `UPDATE` condicional (`expirarSePendente` devolve 1): só audita quando a
      linha mudou, para rodar duas vezes não duplicar
- [ ] Gerações automáticas da sprint 4 (job de missas) **não** geram um registro por missa: um por execução
      (`MISSAS_GERADAS`, contagem e intervalo); evento criado pela coordenação: `CELEBRACAO_CRIADA`
- [ ] `detalhe` truncado em 255 sem estourar (há uma função de corte única, não `substring` em cada lugar)

Cuidados com testes existentes:

- [ ] Todos os ITs de escala continuam passando; só ganham linhas em `audit_log`. Os que contam registros
      (`auditLogRepository.count()`/`findAll()` esperando um número fixo) precisam filtrar por `tipoEvento`
- [ ] ITs `@Transactional` (`AlocacaoRegrasIT`, `EntrePastoraisIT`, `PermissaoPorPapelIT`) gravam a auditoria dentro
      do teste e fazem rollback junto: conferir com `auditLogRepository` na mesma transação
- [ ] `ConviteRespostaIT`/`ConvitePublicoIT` (sem `@Transactional`): a auditoria do link público sai no commit

Testes novos — `AuditoriaEscalaIT`:

- [ ] Para cada linha da tabela acima, uma ação real gera **exatamente um** registro com o tipo, o `pastoralId` e o
      `usuarioId` certos (a tabela inteira numa tabela de casos)
- [ ] Sorteio de uma celebração com 3 convidados e 1 vaga incompleta grava **um** `ESCALA_SORTEADA`, com as contagens
- [ ] Ação recusada (409, 422, 403, 404) **não** grava registro; ação que falha no meio faz rollback do registro
- [ ] Convite respondido pelo link: autor é a pessoa convidada; expirado pelo job: um registro mesmo rodando duas vezes
- [ ] `detalhe` nunca contém e-mail, telefone nem token (varrer os registros de todas as ações acima)

### Cartão 3 — Auditoria de membros, papéis e perfis

Problema: dar ou tirar o papel de coordenador, mudar o perfil, desativar uma conta e cadastrar gente não deixam rastro
(só o cadastro de membro e a troca de telefone, da sprint 2, deixam).

Ler: `service/{UsuarioService, UsuarioPastoralService, UsuarioFuncaoService, MeService}`,
`service/conta/{CadastroMembroService, AtivacaoContaService}`, `controller/{UsuarioController,
UsuarioPastoralController}`, `security/SessaoService`.

- [ ] Eventos (mesma regra: mesma transação, ids no `detalhe`, nunca e-mail/telefone):

  | Ação | Tipo | Onde |
  |---|---|---|
  | Criar conta (`POST /api/usuarios`) | `USUARIO_CRIADO` (`detalhe`: perfil) | `UsuarioService.criar` |
  | Mudar perfil, e-mail ou senha por PADRE/ADMIN | `USUARIO_ALTERADO` (`detalhe`: quais campos mudaram, **nunca** os valores) | `UsuarioService.atualizar` |
  | Desativar conta | `USUARIO_DESATIVADO` | `UsuarioService.desativar` |
  | Dar, trocar ou tirar papel numa pastoral | `PAPEL_CRIADO`, `PAPEL_ALTERADO` (`detalhe`: antes e depois), `PAPEL_REMOVIDO` | `UsuarioPastoralService` |
  | Marcar/desmarcar habilidade | `HABILIDADE_ALTERADA` (já existe da sprint 3; só passa a gravar o `pastoralId`) | `UsuarioFuncaoService` |

- [ ] Eventos que já existem (`MEMBRO_CADASTRADO`, `CONTA_ATIVADA`, `TELEFONE_ALTERADO`, `SENHA_REDEFINIDA`,
      `CONTA_PROTEGIDA`) passam a gravar o `pastoralId` quando houver (cadastro de membro), sem mudar o nome
- [ ] `USUARIO_ALTERADO` com troca de e-mail grava só "e-mail alterado": o e-mail novo e o antigo **não** entram no log
      (já são dados da conta; o log serve para saber quem mexeu e quando)

Cuidados com testes existentes:

- [ ] `UsuarioContaIT`, `UsuarioPastoralIT`, `PermissaoPorPapelIT`, `SessaoIT`: continuam passando, com linhas novas em
      `audit_log`; os que contam registros filtram por tipo

Testes novos — `AuditoriaMembrosIT`:

- [ ] Cada ação da tabela gera um registro com tipo, `usuarioId` do autor e `pastoralId` (quando houver) certos
- [ ] `PAPEL_ALTERADO` traz papel antigo e novo; `USUARIO_ALTERADO` por troca de e-mail não traz e-mail nenhum
- [ ] Alteração recusada (403, 404, 409, 422) não grava; PADRE e coordenador são o `usuarioId` quando agem

### Cartão 4 — Financeiro sem edição: estorno em vez de exclusão

Problema: o lançamento nunca é editado, mas pode ser **apagado** (`DELETE`, exclusão lógica), e some do saldo e do
resumo sem deixar rastro. Num caixa, o erro se corrige com um **estorno**: um lançamento novo, de tipo oposto, ligado
ao original e com motivo. Nada some, tudo soma.

Ler: `service/LancamentoFinanceiroService`, `controller/LancamentoFinanceiroController` (`/api/pastorais/{id}/financeiro`),
`controller/FinanceiroController` (`/api/financeiro/resumo`), `model/LancamentoFinanceiro`,
`model/enumerated/TipoLancamento`, `model/dto/{LancamentoFinanceiroRequestDTO, LancamentoFinanceiroResponseDTO,
SaldoPastoralDTO, ResumoFinanceiroDTO}`, `repository/LancamentoFinanceiroRepository`, `mapper/LancamentoFinanceiroMapper`,
`security/PastoraisPermissao` (`podeLerFinanceiro`, `podeEscreverFinanceiro`), migration `V7`, `pastoral/FinanceiroIT`.

- [ ] **Migration B:** `lancamento_financeiro` ganha `estorno_de_id BIGINT NULL REFERENCES lancamento_financeiro` e
      `motivo_estorno VARCHAR(255) NULL`; índice único parcial `(estorno_de_id) WHERE estorno_de_id IS NOT NULL` (um
      lançamento só pode ser estornado **uma vez**); `CHECK` que `motivo_estorno` só existe quando há `estorno_de_id`.
      Os lançamentos que já foram apagados logicamente (`is_active = false`) ficam como estão (ver Pendente/decisões)
- [ ] `POST /api/pastorais/{pastoralId}/financeiro/{id}/estornar` com `{ motivo }` (obrigatório, até 255): cria um
      lançamento de **tipo oposto**, mesmo valor, `descricao` "Estorno: <descrição original>" (cortada em 255), data de
      **hoje** (fuso `servio.fuso`), `estorno_de_id` e `motivo_estorno`. Devolve 201 com o lançamento criado. Só quem pode
      escrever (tesoureiro da pastoral e ADMIN; padre só lê). Na mesma transação, **trava o lançamento original**
      (`select for update`) para dois estornos simultâneos não passarem do índice único (um recebe 409)
- [ ] Regras: estornar um estorno → 422 "Um estorno não pode ser estornado" (se errou, lança de novo); estornar duas
      vezes → 409; lançamento de outra pastoral ou de outra paróquia → 404 (como `obter` hoje)
- [ ] **`DELETE .../{id}` deixa de apagar:** passa a responder **405** ("Lançamentos não são apagados: use o estorno").
      O `desativar` do service é removido. (Quebra de contrato: ver Pendente/decisões; o front de Financeiro é desta
      sprint e já nasce usando o estorno)
- [ ] **Saldo e resumo:** continuam somando por tipo, então o estorno cancela o original sem código novo (+100 de
      entrada, +100 de saída = 0). O `LancamentoFinanceiroResponseDTO` ganha `estornoDeId`, `motivoEstorno` e
      `estornado` (verdadeiro quando existe um estorno apontando para ele; calculado em lote na listagem, sem N+1),
      para a tela riscar o original. O resumo do padre não muda de formato
- [ ] **Auditoria** (cartão 1): `LANCAMENTO_CRIADO` (`detalhe`: tipo e valor, **sem descrição**, que pode ter nome de
      pessoa) e `LANCAMENTO_ESTORNADO` (`referenciaId` = o original; `detalhe`: id do estorno e valor), ambos com
      `pastoralId`
- [ ] Valor e tipo de um lançamento continuam **imutáveis** (não existe `PUT`); a criação mantém `@Positive` no valor

Cuidados com testes existentes:

- [ ] `FinanceiroIT`: os testes que apagam um lançamento (`DELETE`) e conferem o saldo mudam para 405 + estorno;
      acertar os asserts de lista (o original continua listado) e de saldo
- [ ] `PermissaoPorPapelIT`/`EntrePastoraisIT` (se tiverem o `DELETE` do financeiro): trocar para o estorno
- [ ] `ResumoFinanceiroDTO`/`SaldoPastoralDTO`: formato inalterado

Testes novos — em `FinanceiroIT` e `AuditoriaFinanceiroIT`:

- [ ] Estornar uma entrada de 100 cria uma saída de 100 ligada ao original; saldo e resumo voltam ao valor de antes
- [ ] Estornar duas vezes: 409; estornar um estorno: 422; estorno sem motivo: 400; motivo de 256 caracteres: 400
- [ ] Dois estornos simultâneos do mesmo lançamento: um 201 e um 409, um único estorno gravado (duas threads)
- [ ] Padre e membro não estornam (403); tesoureiro de outra pastoral: 404; tesoureiro da pastoral e ADMIN: 201
- [ ] `DELETE`: 405 e nada muda no saldo
- [ ] Lista marca `estornado` no original e traz `estornoDeId` no estorno; padre vê tudo no resumo consolidado
- [ ] Criar e estornar gravam `LANCAMENTO_CRIADO`/`LANCAMENTO_ESTORNADO` com `pastoralId`, valor e **sem** a descrição

### Cartão 5 — Pedido de reunião que fica registrado

> Pode escorregar para depois do piloto (ver "Se faltar capacidade"): o e-mail de hoje continua funcionando.

Problema: o pedido de reunião é só um e-mail. O coordenador não vê no app quem pediu, nem o que já foi atendido, e o
pedido some se o e-mail se perder.

Ler: `service/ReuniaoService` (`solicitar`, `marcar`), `controller/ReuniaoController`, `model/Reuniao`,
`model/dto/{ReuniaoRequestDTO, SolicitacaoReuniaoRequestDTO, ReuniaoResponseDTO}`, `repository/ReuniaoRepository`,
`service/notification/FilaEmail`, `service/escalacao/PainelService` (onde as pendências do coordenador aparecem hoje),
`security/PastoraisPermissao`, migration `V8`, `pastoral/ReuniaoIT`.

- [ ] **Migration C:** tabela `reuniao_pedido` — `id`, `pastoral_id`, `paroquia_id`, `solicitante_id REFERENCES usuario`,
      `motivo VARCHAR(500) NOT NULL`, `status VARCHAR(12) NOT NULL DEFAULT 'PENDENTE'` com `CHECK` (`PENDENTE`,
      `ATENDIDO`, `RECUSADO`), `reuniao_id BIGINT NULL REFERENCES reuniao`, `respondido_por_id`, `respondido_em`,
      `criado_em`; índice `(pastoral_id, status)`. (`is_active`/`version` seguem o padrão `TenantEntity`)
- [ ] `POST /api/pastorais/{id}/reunioes/solicitar` **continua com o mesmo corpo e as mesmas regras de hoje**
      (participação ativa, quem já coordena recebe 422, pastoral sem coordenador 404), mas agora **grava o pedido**
      (`PENDENTE`) e **mantém o e-mail** aos coordenadores pela `FilaEmail`, na mesma transação. Devolve 201 com o pedido
      (hoje devolve 204: quebra de contrato pequena, ver Pendente/decisões). Limite: no máximo **3 pedidos pendentes por
      pessoa por pastoral** (o 4º dá 429 "Você já tem pedidos em aberto"), para não virar spam para o coordenador
- [ ] `GET /api/pastorais/{id}/reunioes/pedidos?status=` (padrão `PENDENTE`): o coordenador (e padre/ADMIN) vê todos os
      pedidos da pastoral; **quem pediu vê só os seus**. Resposta: solicitante (id e nome, sem e-mail), motivo, status,
      reunião ligada
- [ ] `POST .../pedidos/{pedidoId}/atender` com os dados de uma reunião (`ReuniaoRequestDTO`): o coordenador marca a
      reunião **e** fecha o pedido (`ATENDIDO`, `reuniao_id`), numa transação. `POST .../pedidos/{pedidoId}/recusar`
      fecha como `RECUSADO` (com motivo opcional de até 255 no `detalhe` da auditoria, não no pedido). Pedido já
      respondido: 409. Só o coordenador da pastoral (e padre/ADMIN)
- [ ] `POST .../reunioes` (marcar direto) **não muda**, mas passa a gravar `REUNIAO_MARCADA` na auditoria; atender
      pedido grava `REUNIAO_PEDIDO_ATENDIDO` e recusar `REUNIAO_PEDIDO_RECUSADO`; criar o pedido, `REUNIAO_PEDIDO_CRIADO`.
      Todos com `pastoralId`, sem o texto do motivo
- [ ] Sem notificação extra ao solicitante nesta sprint (a tela mostra o status); avisar por e-mail/sino fica para depois

Cuidados com testes existentes:

- [ ] `ReuniaoIT` (`@Transactional`: o `afterCommit` da fila nunca roda): hoje confere as linhas de `email_saida` por
      coordenador, e continua valendo; o teste de "sem coordenador → 404" não pode gravar pedido (rollback)
- [ ] `solicitar` agora responde 201 com corpo: acertar o `status().isNoContent()` dos testes atuais

Testes novos — `PedidoReuniaoIT`:

- [ ] Pedido grava `reuniao_pedido` `PENDENTE` e **continua** enfileirando um e-mail por coordenador (mesma contagem de hoje)
- [ ] 4º pedido pendente da mesma pessoa na mesma pastoral: 429; depois de atendido/recusado um, volta a aceitar
- [ ] Coordenador lista todos; quem pediu lista só os seus; outro membro vê vazio; pastoral de outra paróquia: 404
- [ ] Atender cria a reunião e fecha o pedido numa transação (se marcar falha, o pedido continua `PENDENTE`);
      atender ou recusar um pedido já respondido: 409; membro e vice: 403
- [ ] Eventos de auditoria dos quatro casos, sem o texto do motivo

## Se faltar capacidade

O cronograma já avisa que esta sprint **pode sair do MVP** e ir para depois do piloto (15/02 a 21/03). Ordem de corte,
do que mais importa para o que menos:

1. **Cartões 1 e 2 (auditoria da escala)**: é o "pronto quando" e o que a Pascom e o padre mais cobram ("quem mexeu na
   escala?"). Se a sprint for cortada, **esses dois ficam**.
2. **Cartão 4 (financeiro sem edição)**: o `DELETE` que apaga sem rastro é um risco **de dados**, não de conforto;
   mesmo cortada a sprint, vale ao menos desligar o `DELETE` e registrar `LANCAMENTO_CRIADO` (a parte pequena do cartão)
   antes do piloto, deixando o estorno completo para depois.
3. **Cartão 3 (auditoria de membros e papéis)**: importante, mas o sistema de hoje já registra o cadastro de membro;
   escorrega bem para depois do piloto.
4. **Cartão 5 (pedido de reunião registrado)**: o e-mail de hoje atende; é o primeiro a cair.

## Contrato combinado (proposta do back-end; ver `sprint-0.md`, seção 5)

> O "Mapa de endpoints" do front não estava disponível quando este documento foi escrito: **nomes e formatos abaixo
> são a proposta do back-end** e precisam ser confirmados com o colaborador antes do cartão 1.

- [ ] `GET /api/audit-logs`: passa a `Page` com filtros `tipo`, `pastoralId`, `usuarioId`, `de`, `ate`; resposta com
      `pastoralId` e `usuarioNome` (**quebra de contrato**: hoje é um array sem filtro)
- [ ] `POST /api/pastorais/{id}/financeiro/{lancamentoId}/estornar` `{ motivo }` → 201; `DELETE .../financeiro/{id}` →
      405; `LancamentoFinanceiroResponseDTO` ganha `estornoDeId`, `motivoEstorno`, `estornado`
- [ ] `POST /api/pastorais/{id}/reunioes/solicitar` → 201 com o pedido (hoje 204); `GET .../reunioes/pedidos`,
      `POST .../pedidos/{id}/atender`, `POST .../pedidos/{id}/recusar`
- [ ] Mensagens que o front mostra: lançamento já estornado, estorno de estorno, pedidos em aberto demais, pedido já
      respondido, "lançamentos não são apagados"

## Fechamento da sprint

- [ ] Atualizar `Features/features-implementadas.md` e `Features/erros-conhecidos.md` com o que mudou
- [ ] Atualizar `Features/checklist-seguranca.md`: auditoria é imutável pela API (só `GET`), o `detalhe` não carrega
      dado pessoal, o caixa não apaga
- [ ] Atualizar `Features/runbook-piloto.md`: onde olhar a auditoria, como estornar um lançamento errado
- [ ] Regenerar o cliente do front (`npm run gen:api`, com Postgres no ar e `OPENAPI_ENABLED=true`): `GET /api/audit-logs`
      mudou de formato, o financeiro ganhou o estorno e a reunião ganhou os pedidos
- [ ] Resumo por cartão: o que mudou, testes novos, e qualquer coisa que ficou pendente ou decidida diferente do
      planejado (com o motivo)
- [ ] Conferir o "Pronto quando": num ambiente de teste, sortear uma celebração, trocar uma pessoa, lançar uma entrada e
      estorná-la; os quatro aparecem em `GET /api/audit-logs`, com autor e pastoral

## Pendente/decisões

Dúvidas reais deste planejamento. Nenhuma foi decidida por palpite: a coluna "padrão proposto" é o que o código faz
**se ninguém decidir diferente**, para não travar o cartão.

| Dúvida | Padrão proposto | Cartão |
|---|---|---|
| Esta sprint entra no MVP ou vai para depois do piloto (risco do cronograma)? | Decidir no fim da sprint 5, olhando a capacidade. Se cortar, ficam os cartões 1, 2 e o mínimo do 4 (ver "Se faltar capacidade") | todos |
| Quem lê a auditoria: só PADRE/ADMIN (como hoje) ou também o coordenador da pastoral? | Também o coordenador, **só dos registros da própria pastoral** (`pastoral_id`). Registro sem pastoral (login, ADMIN) só PADRE/ADMIN | 1 |
| `GET /api/audit-logs` passa de array para `Page`: quebra de contrato | Aceita: o front ainda não usa a rota; o cartão avisa o colaborador antes de começar | 1 |
| Retenção da auditoria (por quanto tempo guardar) e LGPD | Nada é apagado nesta sprint. O `detalhe` guarda só ids e números, nunca dado pessoal, então não cria passivo. Prazo de retenção e eventual job de arquivamento ficam para depois do piloto | 1 |
| `audit_log.detalhe` tem 255 caracteres: cabe "antes e depois" de papel e as contagens do sorteio, mas não texto livre | Cortar em 255 com uma função única; texto livre (motivo de estorno, de recusa) fica na coluna própria de cada tabela, não no log | 1 a 5 |
| Auditoria por chamada (`GET`) de leitura sensível (ex.: quem abriu o financeiro)? | Não: só alterações. Leitura auditada seria outra decisão (volume e privacidade) | 1 |
| Estorno é um lançamento de **tipo oposto** com `estorno_de_id`: as colunas "entradas" e "saídas" do resumo mostram o estorno somado (entrada 100 + saída 100 de estorno). Alternativa: excluir original e estorno dos totais por tipo e só mostrar o saldo | Somar os dois por tipo (o saldo fecha sem código novo e a tela pode riscar o original). Se o padre estranhar as colunas, ajustar só a consulta do resumo | 4 |
| Lançamentos apagados antes desta sprint (`is_active = false`) | Ficam como estão (fora do saldo). Se forem lançamentos de teste, nada a fazer; se houver dado real apagado, é preciso recuperar à mão antes do piloto | 4 |
| `DELETE` do financeiro vira 405: quebra de contrato | Aceita: o front de Financeiro é desta sprint e já nasce usando o estorno | 4 |
| Estornar um estorno? | Não (422): se o estorno estava errado, lança de novo. Evita cadeia de lançamentos ligados | 4 |
| Estorno com data de hoje ou da data do original? | De hoje (o caixa do mês já fechado não muda); a ligação ao original mostra de qual data veio | 4 |
| `solicitar` reunião passa de 204 para 201 com corpo | Aceita (pequena); o front de "Pedir reunião" é desta sprint | 5 |
| Avisar o solicitante quando o pedido for atendido ou recusado | Não nesta sprint (a tela mostra o status). Aviso por e-mail ou sino fica para depois | 5 |
| Reunião marcada pelo coordenador avisa os membros da pastoral? | Não faz hoje e **não entra** nesta sprint (só o pedido vai ao coordenador) | 5 |
| Editar ou cancelar reunião já marcada | Não existe hoje (`ReuniaoController` só lista, marca e solicita) e não entra nesta sprint; registrar o pedido não muda isso | 5 |
| Nomes de rotas e campos (sem o "Mapa de endpoints") | Proposta no "Contrato combinado"; confirmar com o colaborador antes do cartão 1 | 1 a 5 |

## Fora desta sprint

- [ ] Telas Início da gestão, Mais, Financeiro, Reuniões e Pedir reunião: trabalho do colaborador
- [ ] Auditoria de leitura, retenção/arquivamento, exportação da auditoria (PDF/planilha), relatórios financeiros
      (`features-futuras.md`, "Relatórios"), editar/cancelar reunião, avisar membros de reunião marcada, antes/depois
      estruturado em coluna JSON (o `detalhe` de 255 caracteres basta por enquanto)

Ver também: `sprint-3.md`, `sprint-2.md`, `sprint-1.md`, `sprint-0.md`, `features-futuras.md`.
