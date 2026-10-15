# Servio — Sprint 4

**Período:** 07/12 a 20/12/2026 · **Back-end:** Zep · **Front-end:** colaborador

Agenda e celebrações. **Pronto quando:** as missas de janeiro aparecem sozinhas e o prazo do convite nunca passa
da hora da missa.

Stack: Spring Boot 4.1.1, Java 25, Maven wrapper, JPA/Hibernate, Lombok, MapStruct, Flyway, PostgreSQL 17,
Spring Session JDBC, Spring Security com `formLogin`, Thymeleaf para e-mails, Testcontainers nos ITs (`*IT`).

Detalhado a partir de `features-futuras.md` (seção "Sprint 4" e "Decisões pendentes") e de `sprint-1.md`/
`sprint-2.md`; ver também `sprint-0.md`. Escopo desta sprint é **só back-end**: a agenda por semana no celular, o
calendário do mês no computador (decidido em 14/10: calendário do mês com painel ao lado), Escala da missa, Escolher
pessoa, Novo evento e Configurações com prazos são telas do colaborador (front).

**Pausa de Natal e Ano-Novo: 21/12 a 03/01, sem entregas.** Por isso a sprint fecha em 20/12 com o job de geração
já rodando em produção (ou no servidor de teste): é ele que faz as missas de janeiro aparecerem durante a pausa.

## Pré-requisitos (o que já precisa estar na `main`)

- [ ] Cartões 1 a 6 da sprint 1 e da sprint 2 mergeados. Esta sprint usa, de lá: `FilaEmail` (`V14`), o prazo do
      convite por pastoral (`PRAZO_RESPOSTA` em `pastoral_config`) e a numeração de migrations até `V19`
- [ ] Sprint 3 mergeada **ou** a numeração combinada com ela: as migrations desta sprint começam em `V20`; se a
      sprint 3 também criar migration, usar o próximo número livre da `main` (conferir
      `ls src/main/resources/db/migration` antes de nomear) e ajustar os nomes abaixo
- [ ] Os horários reais das missas de domingo da paróquia (comunidade, hora) e qual deles a Pascom cobre. Sem esses
      dados o job não tem o que gerar (ver Pendente/decisões)

## Regras de trabalho

- [ ] Antes de codar cada cartão: ler as classes citadas nele e mostrar o plano antes de começar
- [ ] Um branch por cartão, a partir do `main`: `feat/horario-missa`, `feat/geracao-missas`,
      `feat/eventos-coordenacao`, `feat/prazo-limitado`, `feat/substituir-justificativa`, `feat/agenda`. O
      cartão 2 depende do 1: empilhar no branch do cartão 1 e dizer isso no PR
- [ ] Commits pequenos, mensagem em português
- [ ] Migrations novas a partir de `V20` (nunca editar migration que já existe; `V14` a `V19` são das sprints 1 e 2)
- [ ] `./mvnw verify` ao fim de cada cartão (precisa do Docker ligado); não passar para o próximo com teste vermelho
- [ ] Não mudar comportamento fora do que está descrito em cada cartão; o que parecer errado no caminho vai pra
      "Pendente/decisões" no fechamento, não é corrigido de lado
- [ ] Nunca logar token, senha nem corpo de e-mail. Jobs logam só contagens e ids (`celebracaoId`, `horarioId`),
      nunca nome nem e-mail de pessoa
- [ ] Todo relógio vem do `Clock` do contexto (`ClockConfig`, fuso `servio.fuso`, padrão America/Fortaleza); nada
      de `LocalDateTime.now()` direto. Data e hora da celebração são locais, no mesmo fuso
- [ ] Comentários e mensagens de erro em português, no mesmo estilo do código atual

## Padrão que os cartões seguem (já existe no código)

- **Celebração** (`model/Celebracao`): `comunidade`, `data`, `hora`, `tipo` (`MISSA_DOMINICAL` ou `EVENTO`),
  `tipoData` (`NORMAL`, `SOLENIDADE`, `FESTA`, `FERIADO`) e `titulo` opcional. Hoje só PADRE/ADMIN criam, editam e
  desativam (`CelebracaoService.validar` → `exigirPadreOuAdmin`); `POST /api/celebracoes` já aplica a cobertura
  automática (`CoberturaAutomaticaService.aplicarNaCriacao`). Quem não é PADRE/ADMIN só vê as celebrações em que a
  própria pastoral tem vaga (`exigirVisivel`)
- **Cobertura automática** (`CoberturaAutomaticaService`): ao nascer uma celebração, **toda** pastoral da paróquia
  com `COBERTURA_AUTOMATICA` ligada recebe as vagas dos seus `modelo_vaga` (`TODOS`, `MISSA_DOMINICAL` ou `EVENTO`),
  sem duplicar a vaga da mesma função. `POST /api/pastorais/{id}/modelos-vaga/aplicar-futuras` faz o mesmo nas
  celebrações futuras
- **Jobs** (`service/escalacao/job`): `ExpiracaoConvitesJob` (a cada 10 minutos) e `LembretesJob` (a cada 15),
  `@ConditionalOnProperty(servio.jobs.enabled, matchIfMissing = true)`, `@Scheduled(cron, zone = "${servio.fuso}")`,
  cada item na própria transação (`TransactionTemplate`) e `try/catch` por item para uma falha não parar o lote.
  `application-test.properties` mantém `servio.jobs.enabled=false`; os ITs chamam o método do job direto, com `Clock`
  controlável (como o `JobsIT`)
- **Prazo do convite** (`ConviteService.novoPrazo(pastoralId)`): hoje é `agora + PRAZO_RESPOSTA` da pastoral (padrão
  24 horas), **sem olhar a hora da celebração**. É chamado em três lugares: `EscalacaoService.novaAlocacao`
  (sorteio, escalar, substituir), `AlocacaoService.atualizar` (troca de vaga/usuário) e
  `AlteracaoPendenteService` (desfazer alteração do vice). O lembrete de resposta (`LembretesJob`) sai a N horas do
  `dataLimiteResposta`, e `ExpiracaoConvitesJob` expira o convite depois dele
- **Autorização por pastoral**: `PastoraisPermissao` (`temPapel`, `visivel`, `ehPadre`, `ehAdmin`); rotas de gestão
  entram em `GESTAO_POR_PASTORAL` (`authenticated`, a regra fina é do service); `/api/comunidades/**` fica em
  `CADASTROS` (só PADRE/ADMIN)

## Escopo (4 cartões do cronograma + 2 pequenos de apoio ao front, nesta ordem)

Os cartões 1 a 4 são o que o cronograma pede para o back-end. Os cartões 5 e 6 existem porque duas telas da
sprint (Escala da missa com justificativa; agenda por semana/mês) não têm hoje a rota de que precisam; se a
capacidade da sprint for menor que a prevista, **cortar 5 e 6 primeiro** (ver Pendente/decisões).

### Cartão 1 — Horário fixo da missa de domingo

Problema: as missas de domingo são criadas à mão, uma a uma, só pelo padre (`POST /api/celebracoes`). E a cobertura
automática dá vaga a **toda** pastoral com a opção ligada em **toda** celebração: numa paróquia com três missas no
domingo, a Pascom ganharia três conjuntos de vagas, mas ela cobre **uma** por domingo. Decisão: a paróquia cadastra
os horários fixos e cada pastoral escolhe qual cobre.

Ler: `model/{Celebracao, Comunidade, ModeloVaga, TenantEntity}`, `service/{CelebracaoService,
CoberturaAutomaticaService, ComunidadeService, ModeloVagaService}`, `controller/{CelebracaoController,
ComunidadeController, ModeloVagaController}`, `config/SecurityConfig` (`CADASTROS`, `GESTAO_POR_PASTORAL`),
`service/escalacao/ConfiguracaoPastoralService` (`coberturaAutomaticaLigada`), migration `V11__eventos.sql`
(`modelo_vaga`: o desenho de tabela de tenant), testes `pastoral/EventosIT`.

- [ ] `V20__horario_missa.sql`:
  - [ ] tabela `horario_missa` — `id BIGSERIAL`, `paroquia_id BIGINT NOT NULL REFERENCES paroquia`,
        `comunidade_id BIGINT NOT NULL REFERENCES comunidade`, `dia_semana SMALLINT NOT NULL DEFAULT 7` com `CHECK`
        entre 1 e 7 (1 = segunda … 7 = domingo, `DayOfWeek` do Java), `hora TIME NOT NULL`, mais as colunas de
        sempre (`is_active BOOLEAN NOT NULL DEFAULT TRUE`, `version BIGINT NOT NULL DEFAULT 0`, `created_at`,
        `updated_at`); índice único parcial `(comunidade_id, dia_semana, hora) WHERE is_active`
  - [ ] tabela `cobertura_missa` — o que cada pastoral cobre: `id`, `paroquia_id`, `pastoral_id BIGINT NOT NULL
        REFERENCES pastoral`, `horario_missa_id BIGINT NOT NULL REFERENCES horario_missa`, colunas de sempre;
        índice único parcial `(pastoral_id, horario_missa_id) WHERE is_active`
  - [ ] `celebracao.horario_missa_id BIGINT NULL REFERENCES horario_missa` (de qual horário fixo a celebração
        nasceu; `NULL` para as criadas à mão) e índice único **sem filtro de `is_active`**
        `(horario_missa_id, data) WHERE horario_missa_id IS NOT NULL`. Sem o filtro, a missa que o padre desativou
        (cancelou) não é recriada pelo job do cartão 2
- [ ] Entidades `HorarioMissa` e `CoberturaMissa` (`TenantEntity`, como `ModeloVaga`), repositórios e mappers;
      enum não é necessário (o dia da semana é `DayOfWeek`, guardado como número)
- [ ] Rotas de horário, só PADRE/ADMIN (entram em `CADASTROS`, como `/api/comunidades/**`):
      `GET/POST /api/comunidades/{comunidadeId}/horarios-missa`, `PUT/DELETE
      /api/comunidades/{comunidadeId}/horarios-missa/{id}` (`DELETE` desativa; `fail-on-unknown-properties` vale).
      Corpo `{ diaSemana?, hora }` (`diaSemana` padrão 7). Comunidade de outra paróquia: 404. Horário repetido: 409
- [ ] Rotas de cobertura, em `GESTAO_POR_PASTORAL` (`/api/pastorais/*/cobertura-missa`): `GET` devolve os horários
      da paróquia com `cobertura: true/false` para a pastoral; `PUT` com `{ horarioMissaIds: [...] }` troca o
      conjunto. Quem altera: COORDENADOR da pastoral, PADRE ou ADMIN (mesmo `exigirGestor` do `UsuarioPastoralService`:
      pastoral fora do alcance dá 404, sem permissão 403). **No máximo um horário por comunidade** para a mesma
      pastoral (a Pascom cobre uma missa por domingo em cada comunidade): dois da mesma comunidade dão 422
      "Escolha um horário por comunidade"
- [ ] `CoberturaAutomaticaService.aplicarNaCriacao`: se a celebração tem `horario_missa_id`, só recebem vagas as
      pastorais com linha ativa em `cobertura_missa` para aquele horário (e `COBERTURA_AUTOMATICA` ligada, como hoje).
      **Celebração sem `horario_missa_id` (criada à mão ou evento) continua exatamente como hoje**: todas as
      pastorais com cobertura ligada. `aplicarFuturas` segue a mesma regra por celebração
- [ ] `AuditLog` `HORARIO_MISSA_ALTERADO` e `COBERTURA_MISSA_ALTERADA` (detalhe com ids e hora, sem nomes de pessoas)

Cuidados com testes existentes:

- [ ] `EventosIT`, `EntrePastoraisIT`, `AlocacaoRegrasIT`: criam celebrações à mão (`horario_missa_id` nulo);
      continuam passando sem mudança
- [ ] `AutorizacaoIT`: acrescentar as rotas novas (sem login 401; membro comum 403; coordenador de outra pastoral 404)
      e `MassAssignmentIT` (campo a mais no corpo é 400)

Testes novos — `HorarioMissaIT`:

- [ ] Padre cria, edita e desativa horário; coordenador tentando criar horário: 403; comunidade de outra paróquia: 404
- [ ] Horário repetido na mesma comunidade: 409; `diaSemana` 0 ou 8: 400
- [ ] Coordenador da Pascom escolhe o horário das 10h: `GET` marca `cobertura: true` só nele; escolher dois horários da
      mesma comunidade: 422; coordenador do ECC não altera a cobertura da Pascom: 404
- [ ] Celebração criada à mão (sem horário) continua dando vaga a todas as pastorais com cobertura ligada
- [ ] Celebração com `horario_missa_id` (criada direto no repositório, como `criarCelebracaoDireta`) dá vaga só à
      pastoral que cobre aquele horário; a que cobre outro horário não ganha nada

### Cartão 2 — Job que mantém 8 semanas geradas

Problema: sem alguém criando as missas à mão, o mês seguinte fica vazio e ninguém é sorteado. Decisão: um job
diário mantém sempre as próximas 8 semanas de missas geradas a partir dos horários fixos. Empilhar no cartão 1.

Ler: `service/escalacao/job/{ExpiracaoConvitesJob, LembretesJob}` (padrão de job, `TransactionTemplate`),
`service/{CelebracaoService, CoberturaAutomaticaService}`, `config/ClockConfig`, `application.properties`
(`servio.jobs.enabled`, `servio.fuso`), testes `pastoral/JobsIT` (relógio controlável).

- [ ] Propriedade `servio.agenda.semanas-a-frente=8`
- [ ] `GeracaoMissasService` (`service/agenda`): `gerar(horario, ate)` cria uma `Celebracao` `MISSA_DOMINICAL`
      (`tipoData` `NORMAL`, `titulo` nulo, `hora` do horário, `horario_missa_id` preenchido) para cada data cujo
      `DayOfWeek` bate com o horário, de **hoje** até `hoje + 8 semanas`, que ainda não exista (o índice único do
      cartão 1 decide; duas execuções ao mesmo tempo não duplicam), e chama a cobertura automática (que respeita
      `cobertura_missa`). Devolve quantas criou. Não cria no passado; nunca recria a missa desativada
- [ ] `GeracaoMissasJob` (`@ConditionalOnProperty servio.jobs.enabled`, mesmo padrão dos outros jobs):
  - [ ] diário de madrugada (`0 30 3 * * *`, `zone = "${servio.fuso}"`): para cada horário ativo de todas as
        paróquias, na própria transação, com `try/catch` por horário; log só com contagem e `horarioId`
  - [ ] também uma vez ao subir a aplicação (`ApplicationReadyEvent`), para um deploy não esperar até a madrugada
        seguinte (desligado em teste pela mesma propriedade)
- [ ] Ao criar um horário (cartão 1) ou ligar uma cobertura, chamar `gerar` na hora (na transação de quem chamou),
      para o padre ver as missas sem esperar o job. Editar a `hora` de um horário vale **só para as datas ainda não
      geradas**; as já geradas ficam como estão (o padre ajusta à mão, ver Pendente/decisões)
- [ ] Horário desativado deixa de gerar; as celebrações futuras já geradas **não** são apagadas (podem ter escala)
- [ ] Nenhum e-mail sai da geração (as vagas nascem sem convite; quem sorteia é a coordenação)

Cuidados com testes existentes:

- [ ] `application-test.properties` continua com `servio.jobs.enabled=false`; o novo job não roda nos ITs que já
      existem, e `JobsIT` não muda
- [ ] `EventosIT` (cobertura automática): como nenhum horário fixo existe nesses testes, nada novo é gerado

Testes novos — `GeracaoMissasIT` (sem `@Transactional` onde precisar comitar, `Clock` controlável como no `JobsIT`):

- [ ] Relógio em 14/12/2026 (segunda), horário de domingo às 10h: `gerar` cria os domingos de 20/12 a 08/02 (8
      semanas à frente), todos `MISSA_DOMINICAL` às 10h com o `horario_missa_id`; as missas de janeiro existem sem
      ninguém ter criado
- [ ] Rodar de novo: 0 criadas (idempotente); avançar o relógio 7 dias: só a semana nova aparece
- [ ] Padre desativa a missa de 04/01 e o job roda: ela **não** volta
- [ ] Duas execuções simultâneas (`ExecutorService`, como o teste de `consumir` do token): nenhuma data duplicada
- [ ] Pastoral que cobre o horário das 10h ganha as vagas do modelo nas missas geradas; a que cobre outro horário não;
      a que não escolheu nenhum não ganha nada (ver Pendente/decisões)
- [ ] Horário desativado: não gera, e as celebrações futuras já geradas continuam lá
- [ ] Editar a hora do horário: as geradas ficam com a hora antiga, as novas saem com a nova
- [ ] Duas paróquias com horários próprios: cada uma só recebe as suas (isolamento, como `TenantIsolamentoIT`)

### Cartão 3 — Coordenação cria eventos

Problema: evento (`tipo = EVENTO`: retiro, festa, quermesse) só o padre cria, mas quem sabe da necessidade é a
coordenação da pastoral. Decisão: o coordenador cria o evento da **sua** pastoral, com as vagas da própria pastoral.

Ler: `service/CelebracaoService` (`validar`, `criar`, `atualizar`, `desativar`), `controller/CelebracaoController`,
`service/{CoberturaAutomaticaService, UsuarioPastoralService}` (`exigirGestor`), `security/PastoraisPermissao`,
`config/SecurityConfig` (`GESTAO_POR_PASTORAL`: `/api/pastorais/*/celebracoes/**`), `model/dto/Celebracao*DTO`,
migration `V11__eventos.sql`, testes `pastoral/EventosIT` (`coordenadorCriaVagaOndePastoralNaoEResponsavel...`).

- [ ] `V21__celebracao_pastoral_origem.sql`: `celebracao.pastoral_origem_id BIGINT NULL REFERENCES pastoral` (quem
      criou o evento; `NULL` para o que o padre criou e para as missas) e índice em `(pastoral_origem_id)`
- [ ] `POST /api/pastorais/{pastoralId}/eventos`, corpo `EventoNovoRequestDTO { comunidadeId, data, hora, titulo,
      tipoData? }` (`@Valid`, `fail-on-unknown-properties`; `titulo` **obrigatório** aqui, ao contrário de
      `POST /api/celebracoes`), devolve **201** com o mesmo `CelebracaoResponseDTO` (que ganha `pastoralOrigemId`).
      Cria a celebração com `tipo = EVENTO` e `pastoral_origem_id` = a pastoral da rota
  - [ ] Permissão: COORDENADOR **da pastoral da rota**, PADRE ou ADMIN. VICE, TECNICO, secretário e membro: 403.
        Pastoral fora do alcance ou de outra paróquia: 404 (mesma regra de `exigirGestor`). Comunidade de outra
        paróquia: 404
  - [ ] Data no passado: 422 "A data do evento já passou"
  - [ ] Vagas: aplica os `modelo_vaga` **só da pastoral de origem** (tipo `EVENTO` ou `TODOS`), e só se a cobertura
        automática dela estiver ligada. Evento da Pascom **não** vira vaga do ECC; as outras pastorais continuam
        entrando pelo padre ("responsabilizar a pastoral", V11). `POST /api/celebracoes` do padre não muda
- [ ] `PUT /api/pastorais/{pastoralId}/eventos/{celebracaoId}` e `DELETE` (desativa): só evento **da própria
      pastoral de origem** (senão 404, como se não existisse) e só por quem pode criar. Bloqueia com 422 "Há pessoas
      escaladas neste evento: peça ao padre" se existir alocação `PENDENTE` ou `ACEITA` em qualquer vaga do evento
      (cancelar com aviso aos escalados fica para depois, ver Pendente/decisões). `PUT` não troca a pastoral de
      origem nem o tipo
- [ ] `AuditLog` `EVENTO_CRIADO`, `EVENTO_ALTERADO`, `EVENTO_CANCELADO` (detalhe: data, hora e pastoral; sem pessoas)
- [ ] O que o padre faz em `/api/celebracoes` e `/api/pastorais/*/celebracoes/**` continua igual

Cuidados com testes existentes:

- [ ] `EventosIT`: `POST /api/celebracoes` por coordenador continua 403; o criado pelo padre continua com
      `pastoral_origem_id` nulo
- [ ] `EntreParoquiasIT`, `EntrePastoraisIT`: sem mudança (usam o caminho do padre)
- [ ] `MassAssignmentIT`: rota nova entra (`pastoralOrigemId`, `tipo` ou `paroquiaId` no corpo é 400)

Testes novos — `EventoCoordenacaoIT`:

- [ ] Coordenador da Pascom cria evento: 201, `tipo` `EVENTO`, `pastoralOrigemId` da Pascom, vagas só da Pascom (o
      modelo do ECC não gera vaga)
- [ ] Vice, membro e coordenador do ECC na rota da Pascom: 403; pastoral de outra paróquia: 404; padre e ADMIN: 201
- [ ] Data no passado: 422; sem título: 400; comunidade de outra paróquia: 404
- [ ] Coordenador edita e cancela o próprio evento; evento de outra pastoral ou do padre: 404
- [ ] Evento com convite `PENDENTE` ou `ACEITA`: editar e cancelar dão 422 e nada muda; sem ninguém escalado, cancelar
      funciona e a celebração some das listas
- [ ] O evento criado aparece em `GET /api/celebracoes` para o coordenador (ele tem vaga) e não para membro de outra
      pastoral (`exigirVisivel`)

### Cartão 4 — Prazo do convite limitado pela hora do evento

Problema: o prazo de resposta é `agora + 24 horas` (ou o que a pastoral configurou) **sem olhar a hora da
celebração**. Convite mandado na véspera de uma missa às 10h pode ter prazo até as 18h do dia da missa, e a pessoa
"aceita" depois de a missa ter acontecido. Decisão: o prazo nunca passa da missa.

Ler: `service/escalacao/{ConviteService, EscalacaoService (novaAlocacao), ConfiguracaoPastoralService,
RespostaConviteService}`, `service/{AlocacaoService (atualizar), AlteracaoPendenteService (desfazer),
CelebracaoService (atualizar)}`, `service/escalacao/job/{ExpiracaoConvitesJob, LembretesJob}`,
`repository/AlocacaoRepository` (`idsPendentesVencidos`, `expirarSePendente`, `candidatosLembreteResposta`), testes
`pastoral/{JobsIT, ConviteRespostaIT, SorteioIT, EscalacaoManualIT, AlteracaoPendenteIT}`.

- [ ] `ConviteService.novoPrazo(pastoralId, celebracao)` (a assinatura antiga **some**, para nenhum chamador
      esquecer): `min(agora + PRAZO_RESPOSTA da pastoral, início da celebração − margem)`, onde início =
      `data.atTime(hora)` no fuso do `Clock` e a margem é `servio.convite.margem-minutos-antes-do-evento` (padrão
      `0`). Atualizar os três chamadores: `EscalacaoService.novaAlocacao` (a `Vaga` já traz a celebração),
      `AlocacaoService.atualizar` e `AlteracaoPendenteService`
- [ ] Celebração que já começou (início − margem ≤ agora): **422** "Esta celebração já começou" ao sortear,
      escalar, substituir e trocar vaga/usuário (hoje funciona e gera um convite com prazo no futuro de uma missa
      passada). É a única mudança de comportamento do cartão além do prazo; fica documentada no PR
- [ ] Editar `data` ou `hora` de uma celebração (`CelebracaoService.atualizar`, e o `PUT` do cartão 3) recalcula o
      prazo das alocações `PENDENTE` daquela celebração: `min(prazo atual, novo início − margem)`; se o novo início já
      passou, o convite fica com o prazo no passado e o `ExpiracaoConvitesJob` o expira no próximo ciclo. Não
      estende prazo (mudar a missa para mais tarde não reabre convite que já ia vencer antes)
- [ ] O token não muda e nenhum e-mail novo sai ao recalcular (só o `dataLimiteResposta`)
- [ ] `LembretesJob` e `ExpiracaoConvitesJob` **não mudam**: continuam lendo `dataLimiteResposta`. Prazo curto
      demais para o lembrete (o lembrete é a N horas do prazo): o lembrete sai no ciclo seguinte, quase junto do
      convite; aceito nesta sprint (ver Pendente/decisões)
- [ ] O prazo que a pastoral configura (`PUT /api/pastorais/{id}/config/PRAZO_RESPOSTA`) continua sendo o teto;
      a tela pública do convite (`ConfirmacaoDetalhesDTO.prazo`) já mostra o `dataLimiteResposta`, então exibe o
      prazo real sem mudança

Cuidados com testes existentes:

- [ ] Os ITs de escala criam celebrações de 20 a 50 dias à frente (`AlocacaoRegrasIT`, `EntrePastoraisIT`,
      `ConviteRespostaIT`) ou em 2031 (`PainelIT`): o prazo de 24 horas continua valendo, os asserts atuais passam
- [ ] `JobsIT`: confere o prazo de convites criados direto no repositório (não passam por `novoPrazo`); sem mudança.
      Se algum teste usar celebração de hoje ou do passado com `escalar`, ele passa a dar 422 e deve ser ajustado
      para uma data futura (e anotado no PR)
- [ ] `AlteracaoPendenteIT`: o desfazer recalcula o prazo com a celebração da vaga de destino

Testes novos — `PrazoConviteIT` e `ConviteServiceTest` (unitário, sem Spring, `Clock.fixed`):

- [ ] Celebração daqui a 3 dias, prazo de 24h: o prazo é `agora + 24h` (nada muda)
- [ ] Celebração amanhã às 10h, agora 18h do dia anterior, prazo de 24h: o prazo é **amanhã às 10h**, não 18h
- [ ] Celebração daqui a 30 minutos: o prazo é daqui a 30 minutos; celebração que já começou: 422 e nenhuma
      alocação nem e-mail criados
- [ ] Margem de 60 minutos (propriedade): o prazo é 1 hora antes da missa
- [ ] Sortear, escalar à mão, substituir e trocar de vaga respeitam o teto (um teste por caminho)
- [ ] Mudar a missa de 10h para 8h: convite pendente que vencia às 10h passa a vencer às 8h; mudar de 10h para 12h
      não estende
- [ ] Com o relógio depois do novo prazo, `ExpiracaoConvitesJob` expira o convite e o aviso ao coordenador sai uma vez
- [ ] **Invariante** (o "pronto quando"): em todos os cenários acima, `dataLimiteResposta ≤ início da celebração`

### Cartão 5 — Substituir com justificativa (apoio à tela "Escala da missa")

Problema: a tela Escala da missa tem "Substituir" com campo de justificativa, mas `POST
/api/alocacoes/{id}/substituir` só recebe `{ usuarioId, forcar }` (`EscalarRequestDTO`) e a justificativa que existe
em `alocacao` (`justificativa`) é a **recusa** de quem foi convidado (`RespostaConviteService`). Cartão pequeno e
**cortável** (ver Pendente/decisões).

Ler: `controller/EscalacaoController` (`substituir`), `model/dto/EscalarRequestDTO`, `service/escalacao/EscalacaoService`
(`substituir`), `model/Alocacao`, `model/dto/AlocacaoResponseDTO`, `service/AuditLogService`,
`service/escalacao/evento/AlocacaoSubstituidaEvent` e quem o escuta (`NotificacaoListener`).

- [ ] `V22__alocacao_motivo_substituicao.sql`: `alocacao.motivo_substituicao VARCHAR(500) NULL`
- [ ] `POST /api/alocacoes/{id}/substituir` aceita `justificativa` **opcional** (`@Size(max = 500)`, texto aparado;
      vazio vira `null`) num DTO próprio `SubstituirRequestDTO { usuarioId, forcar, justificativa? }` (o
      `EscalarRequestDTO` do `escalar` não muda). A justificativa é gravada na alocação **substituída**
      (`motivo_substituicao`) e entra no `AuditLog` da substituição
- [ ] `AlocacaoResponseDTO` mostra `motivoSubstituicao` só para quem gerencia a pastoral (coordenador, vice, padre,
      ADMIN); para os demais, `null`
- [ ] O e-mail "você foi substituído" **não** leva o texto livre (ver Pendente/decisões)
- [ ] Sem `justificativa` o comportamento é idêntico ao de hoje

Testes novos — em `EscalacaoManualIT`:

- [ ] Substituir com justificativa grava `motivo_substituicao` na alocação antiga; sem justificativa, `null`
- [ ] Justificativa de 501 caracteres: 400; só espaços: gravada como `null`
- [ ] Coordenador vê `motivoSubstituicao`; membro que lista a escala da missa vê `null`
- [ ] O e-mail da substituída não contém a justificativa (capturar as variáveis enfileiradas)

### Cartão 6 — Agenda por intervalo (apoio ao celular e ao calendário do mês)

Problema: a agenda do celular (semana) e o calendário do mês no computador precisam, numa chamada só, das
celebrações do período com a escala **do próprio usuário** em cada uma. Hoje o front teria de juntar
`GET /api/celebracoes?de&ate` (paginado, 20 por página) com `GET /api/me/escalas?de&ate`. Cartão pequeno e
**cortável** (ver Pendente/decisões). O painel ao lado do calendário usa o `GET /api/pastorais/{id}/painel?mes=`
que já existe.

Ler: `service/CelebracaoService` (`listar` por período, visibilidade), `service/leitura/LeituraEscalaService`
(`minhasEscalas`), `controller/MeController`, `repository/{CelebracaoRepository, AlocacaoRepository}`
(`escalasDoUsuario`), `model/dto/EscalaDTOs`, teste `pastoral/LeituraIT` (conta consultas SQL: o padrão para provar
que não há N+1).

- [ ] `GET /api/agenda?de=AAAA-MM-DD&ate=AAAA-MM-DD` (autenticado, em `GESTAO_POR_PASTORAL` ou `authenticated`,
      como `/api/me/**`): lista de dias com as celebrações visíveis ao usuário (mesma regra de
      `CelebracaoService.listar`: padre/ADMIN veem tudo da paróquia, os demais só onde a pastoral deles tem vaga) e,
      em cada uma, `minhaEscala` (a alocação do próprio usuário, se houver: pastoral, função, status do convite,
      `dataLimiteResposta`). Ordenado por data e hora
- [ ] Período: `de` e `ate` obrigatórios, `ate` não antes de `de`, no máximo **62 dias** (cobre um mês no
      calendário com as semanas de borda); acima disso 422 "Período grande demais"
- [ ] Sem N+1: o número de consultas SQL não cresce com o número de celebrações (mesmo teste de `LeituraIT`)
- [ ] Nada de dado de outra pessoa além do que `escala` já mostra a quem é da pastoral

Testes novos — `AgendaIT`:

- [ ] Membro vê só as celebrações em que a pastoral dele tem vaga e `minhaEscala` só nas dele; coordenador vê as da
      pastoral; padre vê todas da paróquia; outra paróquia: nada
- [ ] Mês com 4 e com 20 celebrações: mesmo número de consultas SQL
- [ ] `ate` antes de `de`: 422; 63 dias: 422; sem parâmetros: 400; sem login: 401

## Contrato combinado (rotas novas; ver `sprint-0.md`, seção 5)

> O "Mapa de endpoints" do front ainda não estava disponível quando este documento foi escrito: **nomes e formatos
> abaixo são a proposta do back-end** e precisam ser confirmados com o colaborador antes do cartão 1.

- [ ] `GET/POST /api/comunidades/{id}/horarios-missa` e `PUT/DELETE .../{horarioId}` `{ diaSemana?, hora }` (PADRE/
      ADMIN); 409 horário repetido
- [ ] `GET/PUT /api/pastorais/{id}/cobertura-missa` `{ horarioMissaIds }`; 422 dois horários da mesma comunidade
- [ ] `POST /api/pastorais/{id}/eventos` `{ comunidadeId, data, hora, titulo, tipoData? }` → 201; `PUT/DELETE
      /api/pastorais/{id}/eventos/{celebracaoId}`; 403 sem ser coordenador; 422 data passada ou pessoas escaladas
- [ ] `CelebracaoResponseDTO`: `pastoralOrigemId` e `horarioMissaId` novos
- [ ] `POST /api/alocacoes/{id}/substituir`: `justificativa` opcional; `AlocacaoResponseDTO.motivoSubstituicao`
- [ ] `GET /api/agenda?de&ate` → dias com celebrações e `minhaEscala`
- [ ] Mensagens de erro que o front vai mostrar (esta celebração já começou, o prazo foi limitado pela hora da
      missa, há pessoas escaladas neste evento, a data do evento já passou, escolha um horário por comunidade,
      período grande demais)

## Fechamento da sprint

- [ ] Atualizar `Features/features-implementadas.md` e `Features/erros-conhecidos.md` com o que mudou
- [ ] Atualizar `Features/checklist-seguranca.md` (rotas novas de coordenação, isolamento por paróquia dos horários
      e eventos) e o `runbook-piloto.md` (como cadastrar o horário fixo, o que fazer se uma missa não foi gerada, como
      cancelar uma missa gerada sem ela voltar)
- [ ] Resumo por cartão: o que mudou, testes novos, e qualquer coisa que ficou pendente ou decidida diferente
      do planejado (com o motivo)
- [ ] Conferir o "Pronto quando": com o horário de domingo cadastrado e o job rodando, as missas de janeiro aparecem
      sem ninguém criar nenhuma; mandar um convite para uma missa de amanhã cedo e conferir que o prazo é a hora da
      missa
- [ ] Conferir, antes da pausa de Natal (até 20/12), que as missas de janeiro e as primeiras de fevereiro já existem
      no servidor de teste

## Pendente/decisões

Dúvidas reais deste planejamento. Nenhuma foi decidida por palpite: a coluna "padrão proposto" é o que o código
faz **se ninguém decidir diferente**, para não travar o cartão.

| Dúvida | Padrão proposto | Cartão |
|---|---|---|
| Quais são as missas de domingo da paróquia (comunidades e horas) e qual delas a Pascom cobre | O padre cadastra pelos endpoints; sem os dados reais o job não gera nada. Pedir a lista à Pascom antes de 07/12 | 1, 2 |
| "A Pascom cobre uma por domingo": e a pastoral que ainda não escolheu qual horário cobre? | Não recebe vaga nas missas geradas (seguro: evita a Pascom ganhar três conjuntos de vagas). Celebração criada à mão continua dando vaga a todas, como hoje | 1 |
| Missas que não são de domingo (sábado à noite, dia de semana) | `dia_semana` já existe na tabela (padrão 7 = domingo) e o job trata qualquer dia, mas o cronograma só pede domingo: a tela do front só oferece domingo nesta sprint | 1, 2 |
| Domingo que é solenidade ou feriado (Natal, Páscoa, Corpus Christi) | A missa gerada sai `NORMAL`; o padre ajusta `tipoData`, hora ou desativa à mão. Nenhum calendário litúrgico é embutido | 2 |
| Mudar a hora de um horário fixo: o que acontece com as missas já geradas? | Nada: só valem as datas ainda não geradas. Quem quiser mudar as futuras edita cada celebração (`PUT /api/celebracoes/{id}`) | 2 |
| Missa gerada que o padre cancelou volta no dia seguinte? | Não: o índice único `(horario_missa_id, data)` não filtra `is_active`. Para "restaurar", reativar à mão no banco ou criar outra à mão | 1, 2 |
| Janela de geração e horário do job | 8 semanas (`servio.agenda.semanas-a-frente`), diário às 03:30 no fuso da paróquia, mais uma vez ao subir a aplicação e ao salvar um horário | 2 |
| Horário desativado: apagar as missas futuras já geradas? | Não: podem ter gente escalada. O padre desativa celebração por celebração | 2 |
| Quem pode criar evento: só COORDENADOR ou também VICE/TECNICO? | Só COORDENADOR da pastoral (e PADRE/ADMIN). Vice e técnico leem e escalam, mas não criam evento. Fácil de abrir depois | 3 |
| Evento criado pela coordenação dá vaga a outras pastorais (ex.: Pascom cria o "Retiro" e quer o ECC)? | Não: só a pastoral de origem. As outras entram quando o padre as responsabiliza, como hoje | 3 |
| Editar ou cancelar evento que já tem gente escalada | Bloqueia com 422 e manda pedir ao padre. Cancelar avisando os escalados (e-mail, expirar convites) fica para uma sprint própria | 3 |
| Evento no passado ou no mesmo dia, mas em hora que já passou | 422 (data) e 422 "já começou" (hora, pelo cartão 4) | 3, 4 |
| Margem entre o prazo e a missa: o convite pode vencer na hora exata da missa? | Margem 0 (`servio.convite.margem-minutos-antes-do-evento=0`), igual ao texto do cronograma ("o prazo nunca passa da missa"). Sugestão para o piloto: 60 a 120 minutos, para dar tempo de achar substituto; é só trocar a propriedade | 4 |
| Convidar para celebração que já começou | 422 "Esta celebração já começou" (hoje funciona e gera convite de missa passada). Muda comportamento; está no escopo do cartão 4 e vai no corpo do PR | 4 |
| Lembrete de resposta quando o prazo ficou curto (missa em poucas horas) | Aceito: o lembrete sai a N horas do prazo, então pode sair quase junto do convite. Ajustar (ex.: não lembrar convite com menos de 1 hora) só se o piloto mostrar incômodo | 4 |
| Mudar a hora da missa com convites pendentes | Recalcula o prazo só para menos (`min(prazo atual, novo início − margem)`); nunca estende e não manda e-mail novo | 4 |
| Fuso horário e horário de verão | Um fuso só (`servio.fuso`, America/Fortaleza, sem horário de verão). Paróquia em outro fuso precisaria de fuso por paróquia (fora do MVP) | 2, 4 |
| Justificativa ao substituir aparece para quem? E vai no e-mail da pessoa substituída? | Só para quem gerencia a pastoral (coordenador, vice, padre, ADMIN) e no `AuditLog`. O e-mail da substituída não leva texto livre da coordenação | 5 |
| Cartões 5 e 6 entram na sprint ou ficam para depois? | Entram só se sobrar capacidade (são pequenos). Sem o 5, a tela esconde o campo de justificativa; sem o 6, o front junta `GET /api/celebracoes` (com `size` alto) e `GET /api/me/escalas` | 5, 6 |
| Janela máxima da agenda e se o `GET /api/celebracoes` paginado serve ao calendário | 62 dias na rota nova; sem ela, o front passa `size` maior que 20 e `de`/`ate` no `GET /api/celebracoes` | 6 |
| Calendário do mês com painel ao lado no computador | Decidido em 14/10; só front. O painel usa o `GET /api/pastorais/{id}/painel` que já existe | 6 |
| Nomes das rotas e dos campos (sem o "Mapa de endpoints") | Proposta no "Contrato combinado"; confirmar com o colaborador antes de começar o cartão 1 | 1 a 6 |
| Pausa de Natal e Ano-Novo (21/12 a 03/01): quem cuida do job se ele falhar? | Sem entregas na pausa, mas o servidor de teste roda; conferir o log do job em 22/12 e 29/12. Uma falha não perde dado: a execução seguinte gera o que faltou | 2 |

## Fora desta sprint

- [ ] Telas de agenda (semana no celular, mês no computador), Escala da missa, Escolher pessoa, Novo evento,
      Configurações com prazos: trabalho do colaborador
- [ ] Cancelar evento ou missa avisando as pessoas escaladas; restaurar missa cancelada; fuso por paróquia;
      calendário litúrgico embutido; agenda do padre (compromissos e `CompromissoAgenda`), que é da Fase 3
- [ ] Troca de escala entre pessoas (sprint 5) e auditoria completa das ações de escala (sprint 6)

Ver também: `sprint-2.md`, `sprint-1.md`, `sprint-0.md`, `features-futuras.md`.
