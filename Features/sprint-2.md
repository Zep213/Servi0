# Servio — Sprint 2

**Período:** 09/11 a 22/11/2026 · **Back-end:** Zep · **Front-end:** colaborador

Ativação e recuperação. **Pronto quando:** um membro novo entra sem que ninguém saiba a senha dele.

Stack: Spring Boot 4.1.1, Java 25, Maven wrapper, JPA/Hibernate, Lombok, MapStruct, Flyway, PostgreSQL 17,
Spring Session JDBC, Spring Security com `formLogin`, Thymeleaf para e-mails, Testcontainers nos ITs (`*IT`).

Detalhado a partir de `features-futuras.md` (seção "Sprint 2" e "Decisões pendentes") e de `sprint-1.md`; ver
também `sprint-0.md`. Escopo desta sprint é **só back-end**: as telas Entrar, Ativar conta, Esqueci a senha e o
e-mail de senha alterada são do colaborador (front).

## Pré-requisitos (o que já precisa estar na `main`)

- [ ] Cartões 1 a 6 da sprint 1 mergeados. Esta sprint usa, de lá: `FilaEmail` e `email_saida` (cartão 1, `V14`),
      `Telefone`/`ChaveLogin`/login por `login` (cartões 3 e 4), `SessaoDuracao` (cartão 5) e `MeController`/
      `MeService` (cartão 6). O cartão 2 (`V15`) também precisa estar na `main` para a numeração das migrations
      ficar sem buraco
- [ ] Domínio do Servio e remetente de e-mail com SPF, DKIM e DMARC configurados (prazo 08/11, `sprint-0.md`
      seção 6). Sem isso o e-mail de ativação cai no spam e o "pronto quando" não se cumpre, por melhor que seja
      o código

## Regras de trabalho

- [ ] Antes de codar cada cartão: ler as classes citadas nele e mostrar o plano antes de começar
- [ ] Um branch por cartão, a partir do `main`: `feat/token-conta`, `feat/ativacao-conta`, `feat/esqueci-senha`,
      `feat/senha-alterada`, `feat/papel-encerra-sessao`, `feat/foto-perfil`. Os cartões 2, 3 e 4 dependem do
      anterior: empilhar no branch do cartão de que dependem e dizer isso no PR
- [ ] Commits pequenos, mensagem em português
- [ ] Migrations novas a partir de `V17` (nunca editar migration que já existe; `V14` a `V16` são da sprint 1)
- [ ] `./mvnw verify` ao fim de cada cartão (precisa do Docker ligado); não passar para o próximo com teste vermelho
- [ ] Não mudar comportamento fora do que está descrito em cada cartão; o que parecer errado no caminho vai pra
      "Pendente/decisões" no fechamento, não é corrigido de lado
- [ ] Nunca logar token (ativação, recuperação, proteção), senha, hash de senha nem corpo de e-mail. Log de e-mail
      mostra no máximo o domínio do destinatário
- [ ] **Todo** e-mail desta sprint sai pela `FilaEmail` (nunca `Notificador` direto)
- [ ] Token só vai por e-mail, e no **fragmento** da URL (`/ativar#token`, como `/convite#token`): fragmento não
      chega ao servidor nem aos logs de acesso. O banco guarda só o SHA-256
- [ ] Comentários e mensagens de erro em português, no mesmo estilo do código atual

## Padrão que os cartões seguem (já existe no código)

O convite é o modelo: `TokenConvite.gerar()` (32 bytes aleatórios, Base64 URL-safe) e `hash()` (SHA-256 em
hexadecimal, 64 caracteres); a rota pública fica em `/api/confirmacoes/**` (`permitAll`), limitada por IP
(`LimiteConfirmacoes`, 30 por minuto); token inexistente, usado ou de registro inativo dá **a mesma 404**; o
vencimento é a única exceção e dá 410, porque quem chega com o token certo já o conhece. Consumo concorrente
decidido por `update ... where ... returning`/contagem de linhas, como `AlocacaoRepository.expirarSePendente`.
A `CSRF` continua ligada nas rotas públicas (o front chama `GET /api/auth/csrf` antes).

## Escopo (6 cartões, nesta ordem)

### Cartão 1 — Token de conta (infraestrutura de ativação, recuperação e proteção)

Problema: o convite guarda o token dentro da própria `alocacao`. Ativação, recuperação e "Proteger minha conta"
precisam do mesmo mecanismo, ligado ao `usuario`, e com tipos e prazos diferentes. Decisão: uma tabela só.

Ler: `service/escalacao/{TokenConvite, ConfirmacaoPublicaService, LimiteConfirmacoes}`,
`controller/ConfirmacaoController`, `repository/AlocacaoRepository` (`expirarSePendente`), `model/EmailSaida` e
`service/notification/FilaEmail` (cartão 1 da sprint 1), `service/escalacao/job/ExpiracaoConvitesJob` (padrão de job),
`config/SecurityConfig` (matchers públicos).

- [ ] `V17__token_conta.sql`: tabela `token_conta` — `id BIGSERIAL`, `usuario_id BIGINT NOT NULL REFERENCES usuario`,
      `tipo VARCHAR(12) NOT NULL` com `CHECK` (`ATIVACAO`, `RECUPERACAO`, `PROTECAO`),
      `token_hash CHAR(64) NOT NULL`, `expira_em TIMESTAMP NOT NULL`, `usado_em TIMESTAMP`,
      `criado_em TIMESTAMP NOT NULL DEFAULT now()`; índice único em `(token_hash)`; índice único parcial
      `(usuario_id, tipo) WHERE usado_em IS NULL` (um token pendente por tipo e pessoa). Todos os três tipos já
      entram no `CHECK` agora para os cartões 4 e seguintes não precisarem alterar a constraint
- [ ] Enum `TipoTokenConta`; entidade `TokenConta` (não é `TenantEntity`, como `EmailSaida`);
      `TokenContaRepository` com `consumir(hash, tipo, agora)` (`@Modifying`: `update ... set usado_em = :agora
      where token_hash = :hash and tipo = :tipo and usado_em is null and expira_em > :agora`, devolve 0 ou 1),
      `buscarPorHash(hash)` (para distinguir "vencido" de "não existe"), `apagarPendentes(usuarioId, tipo)` e
      `apagarAntesDe(limite)`
- [ ] `TokenContaService` (`service/conta`), usando o `TokenConvite` para gerar e fazer o hash (não duplicar) e o
      `Clock` do contexto:
  - [ ] `emitir(usuario, tipo)`: apaga o pendente do mesmo tipo, grava o novo e devolve o **token puro** (só
        para quem chama montar o link). Prazos: `servio.conta.ativacao-dias=7`,
        `servio.conta.recuperacao-minutos=60`, `servio.conta.protecao-dias=7`
  - [ ] `consumir(token, tipo)`: devolve o `Usuario` (ativo) ou lança `TokenContaInvalidoException` (404, mesma
        mensagem para inexistente, usado, de outro tipo ou de usuário inativo) / `TokenContaVencidoException`
        (410). A decisão de consumo é a do `update` condicional (duas chamadas simultâneas: uma só consome)
  - [ ] `invalidar(usuario)`: apaga todos os pendentes (qualquer tipo), usado quando a senha muda
- [ ] `LimiteContaPublica` (`service/conta`): mesmo desenho do `LimiteConfirmacoes` (Caffeine, janela fixa de 1
      minuto), próprio balde, `servio.conta.limite-por-minuto=20` por IP. Não reaproveitar o balde do convite:
      a página de convite aberta não pode gastar a cota de quem está recuperando a senha
- [ ] Job diário de madrugada (`@ConditionalOnProperty servio.jobs.enabled`, junto do `EnvioEmailJob` ou em
      `LimpezaTokensContaJob`): `apagarAntesDe(agora - 30 dias)` de tokens usados ou vencidos
- [ ] Não há endpoint neste cartão: ele só entrega a base que os cartões 2 a 4 usam

Cuidados com testes existentes:

- [ ] `application-test.properties` continua com `servio.jobs.enabled=false`; nada de teste existente toca
      `token_conta`
- [ ] `TokenConviteTest` não muda (o `TokenConvite` é reaproveitado sem alteração)

Testes novos — `TokenContaServiceIT` (sem `@Transactional`, `Clock` controlável como no `JobsIT`):

- [ ] `emitir` grava só o hash (64 caracteres, diferente do token puro) e devolve um token que `consumir` aceita
- [ ] Segundo `consumir` do mesmo token: 404; token de outro tipo: 404; token de usuário desativado: 404
- [ ] Depois do prazo (avançar o relógio 7 dias + 1 minuto para `ATIVACAO`, 61 minutos para `RECUPERACAO`): 410
- [ ] Novo `emitir` do mesmo tipo mata o anterior (o antigo dá 404); tipos diferentes convivem
- [ ] Duas chamadas simultâneas de `consumir` com o mesmo token: uma consome, a outra recebe 404
- [ ] Teste unitário `LimiteContaPublicaTest`: 21ª chamada na janela é recusada, nova janela libera

### Cartão 2 — Cadastro de membro sem senha e link de ativação (7 dias)

Problema: hoje coordenação cria a conta com uma "senha inicial" (`POST /api/usuarios` com `senha`) e passa a senha
por fora, e depois faz uma segunda chamada para colocar a pessoa na pastoral (`Membros.tsx` faz as duas em
sequência e, se a segunda falha, deixa uma conta órfã). Alguém sabe a senha do membro. Decisão: um cadastro numa
operação só, conta sem senha, e a pessoa define a própria senha por um link de 7 dias.

Ler: `service/UsuarioService` (`validar`, `exigirPodeDefinirPerfil`), `service/UsuarioPastoralService`
(`validar`, `exigirGestor`), `controller/{UsuarioController, UsuarioPastoralController, PastoralLeituraController}`
(rota `/api/pastorais/*/membros` já existe para `GET`), `model/Usuario`, `model/dto/{UsuarioRequestDTO,
EscalaDTOs.MembroPastoralDTO}`, `security/{UsuarioDetailsService, UsuarioPrincipal}`, `config/SecurityConfig`
(`GESTAO_POR_PASTORAL`, `/api/usuarios`), `service/escalacao/ConviteService` (`reenviar`: limite de 1 por hora),
`service/AuditLogService`, `config/BootstrapAdmin` (conta que nasce com senha), `frontend/src/paginas/pastoral/Membros.tsx`
(só para entender o fluxo atual), templates `templates/email/convite.{html,txt}`.

- [ ] `V18__usuario_ativacao.sql`: `usuario.senha` passa a aceitar `NULL`; coluna `ativado_em TIMESTAMP`;
      `UPDATE usuario SET ativado_em = now()` para as contas existentes (todas já têm senha). Sem `CHECK`
      ligando as duas colunas: o cartão 4 usa `senha NULL` com `ativado_em` preenchido para "conta protegida"
- [ ] `Usuario`: `senha` deixa de ser `@NotBlank` (continua `@Size(max = 255)`), campo `ativadoEm`, método
      `boolean estaAtivada()`. A resposta de `UsuarioResponseDTO` **não** ganha nenhum campo de senha/hash (hoje
      também não tem; conferir)
- [ ] `UsuarioDetailsService.loadUserByUsername`: conta com `senha NULL` falha **exatamente como senha errada**
      (mesma 401, mesmo custo de BCrypt). Implementar entregando ao `UsuarioPrincipal` um hash BCrypt constante de
      uma senha aleatória descartada (gerado uma vez na subida), nunca `null`, para o `DaoAuthenticationProvider`
      não tomar o caminho rápido de "senha vazia" (que denunciaria quem tem conta pendente)
- [ ] `POST /api/pastorais/{pastoralId}/membros`, corpo `MembroNovoRequestDTO { nome, email, papel? }` (`papel`
      padrão `MEMBRO`), `@Valid` e `fail-on-unknown-properties`; devolve **201** com `MembroCriadoDTO
      { usuarioId, usuarioPastoralId, ativacaoEnviadaPara }` (e-mail mascarado, ex.: `m***@gmail.com`). A rota
      já está coberta por `GESTAO_POR_PASTORAL` (`/api/pastorais/*/membros`); a regra fina é do service
- [ ] `CadastroMembroService` (`service/conta`), **uma transação**: cria o `Usuario` (perfil `SERVIDOR`, sem senha,
      `paroquiaId` da pessoa logada), cria o `UsuarioPastoral`, emite o token `ATIVACAO` e enfileira o e-mail
      `ativacao` na `FilaEmail` (dentro da transação: o e-mail só sai se tudo comitar). Qualquer falha desfaz tudo
      (nunca sobra conta órfã)
  - [ ] Permissões: as **mesmas** de hoje, sem relaxar nenhuma. Extrair as checagens de
        `UsuarioService.exigirPodeDefinirPerfil` e de `UsuarioPastoralService.exigirGestor` para métodos
        reaproveitáveis (sem mudar o comportamento das rotas atuais) e chamar as duas: coordenador da pastoral
        (ou PADRE/ADMIN) cadastra; o papel `COORDENADOR` só PADRE/ADMIN; pastoral fora do alcance dá 404;
        cargos únicos (`TECNICO`, `REDES_SOCIAIS`) e máximo de 2 `SECRETARIO` valem como em
        `UsuarioPastoralService.validar`
  - [ ] E-mail já usado por conta ativa: 409 "Já existe um usuário ativo com este e-mail" (mesma mensagem do
        `UsuarioService.validar`)
  - [ ] `AuditLog` `MEMBRO_CADASTRADO` (referência: id do usuário novo; detalhe sem e-mail completo)
  - [ ] **Não** recebe telefone (a regra do cartão 3 da sprint 1 continua: só a própria pessoa define o dele, pelo
        `PUT /api/me`)
- [ ] Reenvio: `POST /api/pastorais/{pastoralId}/membros/{usuarioId}/reenviar-ativacao` → 204. Mesma permissão do
      cadastro; só para conta **ainda não ativada** (já ativada: 422 "Esta conta já foi ativada"); no máximo um
      por hora por conta (429 com a mesma mensagem do reenvio de convite). Emite token novo (o link antigo morre
      de propósito, como no reenvio manual do convite) e enfileira `ativacao` de novo
- [ ] `GET /api/pastorais/{id}/membros`: `MembroPastoralDTO` ganha `boolean ativada` (para a tela mostrar "convite
      pendente" e o botão de reenviar). Só esse campo novo
- [ ] Rotas públicas (novo `ContaPublicaController`, `/api/contas/**` com `permitAll` no `SecurityConfig`, antes do
      `anyRequest().denyAll()`; `LimiteContaPublica` por IP, 429 como no `ConfirmacaoController`):
  - [ ] `POST /api/contas/ativacao/detalhes { token }` → `{ primeiroNome, email }` (e-mail mascarado), para a tela
        "Olá, Maria" não depender de login. Não altera nada
  - [ ] `POST /api/contas/ativar { token, senha }` → **204**. Valida a senha com a mesma regra do
        `POST /api/me/senha` (8 a 72 caracteres), consome o token `ATIVACAO`, grava o hash BCrypt, `ativado_em` =
        agora, `AuditLog` `CONTA_ATIVADA`. Não abre sessão: o front leva para a tela Entrar (ver Pendente/decisões)
  - [ ] Conta já ativada com token `ATIVACAO` ainda pendente (não deveria existir, mas o reenvio e a recuperação
        podem cruzar): `consumir` falha com a 404 genérica
- [ ] E-mail `ativacao` (`templates/email/ativacao.{html,txt}`), variáveis já formatadas: `assunto`, `nome`
      (primeiro nome), `pastoral`, `convidadoPor` (primeiro nome de quem cadastrou), `prazo` (data e hora,
      fuso `servio.fuso`), `link` = `servio.front-url` + `/ativar#` + token. Texto provisório (rascunho do
      protótipo ainda a revisar, ver Pendente/decisões): o código não pode depender do texto final

Cuidados com testes existentes:

- [ ] `UsuarioContaIT`, `MassAssignmentIT`, `AutorizacaoIT`, `TenantIsolamentoIT`, `VazamentoIT`: continuam
      passando; acrescentar a rota nova em `AutorizacaoIT` (membro comum 403, coordenador de outra pastoral 404)
      e em `MassAssignmentIT` (campo a mais no corpo é 400)
- [ ] `LoginIT`: continua passando; acrescentar o caso da conta sem senha (abaixo)
- [ ] `EmailTemplatesTest`: incluir `ativacao` na lista de templates (e as variáveis novas no mapa de teste)
- [ ] `ApoioTeste`/`CenarioParoquia` continuam criando usuários com senha (não precisam mudar)
- [ ] `POST /api/usuarios` (com `senha`) **continua existindo e funcionando igual** nesta sprint; só o front
      deixa de usá-lo (ver Pendente/decisões)

Testes novos — `CadastroMembroIT` e `AtivacaoIT` (sem `@Transactional` onde precisar comitar de verdade,
`Notificador` mockado, `Clock` controlável):

- [ ] Coordenador cadastra: uma conta sem senha, um `usuario_pastoral` `MEMBRO`, uma linha `PENDENTE` em
      `email_saida` com template `ativacao` para o e-mail certo, resposta 201 sem token nem hash
- [ ] E-mail repetido: 409 e **nada** gravado (nem usuário, nem papel, nem e-mail na fila)
- [ ] Falha ao criar o papel (ex.: segundo `TECNICO`): 422 e a conta também não existe (transação única)
- [ ] Coordenador de outra pastoral: 404; membro comum: 403; `COORDENADOR` como papel pedido por coordenador: 403
- [ ] Conta sem senha não entra: login com qualquer senha, inclusive vazia, dá a **mesma** 401 e a mesma mensagem
      de conta inexistente
- [ ] Ativar com o token do e-mail define a senha e depois o login por e-mail + senha entra (204); `ativado_em`
      preenchido; mesmo token de novo: 404; senha de 7 caracteres: 400
- [ ] Token vencido (relógio +7 dias e 1 minuto): 410, e a conta continua sem senha
- [ ] Reenvio: link antigo morre (404), o novo ativa; conta já ativada: 422; segundo reenvio na hora: 429
- [ ] Limite por IP: 21ª chamada em um minuto a `/api/contas/**` dá 429
- [ ] Nenhum log contém o token (capturar a saída dos logs do teste e procurar o token puro)

### Cartão 3 — Esqueci a senha (link de 1 hora)

Ler: `service/conta/TokenContaService` (cartão 1), `service/MeService` (`trocarSenha`), `security/{SessaoService,
TentativasLogin, ChaveLogin, UsuarioDetailsService}`, `service/escalacao/ConviteService` (limite de reenvio),
`controller/ContaPublicaController` (cartão 2), `security/LoginHandlers`.

- [ ] `POST /api/contas/recuperacao { login }` → **202**, sempre a mesma resposta e o mesmo corpo, exista a conta
      ou não (`login` é e-mail ou telefone, achado pelo `UsuarioDetailsService.buscar`). Se a conta existe, está
      ativa e foi ativada: emite token `RECUPERACAO` (1 hora) e enfileira `recuperacao` para o **e-mail da
      conta**. Conta sem `ativado_em`: não envia nada (ver Pendente/decisões). Não revela, por nenhum caminho
      (corpo, status, tempo de resposta mensurável), se a conta existe. O trabalho mais lento (render do
      e-mail, SMTP) fica na `FilaEmail`, fora da requisição
- [ ] Limites: por IP (`LimiteContaPublica`, 429) e por conta (`LimiteRecuperacao`, Caffeine, no máximo 3
      e-mails por hora por conta; passou disso a resposta continua 202 e **não** envia, para a caixa da pessoa
      não virar alvo de spam). Texto digitado vira chave como no `ChaveLogin` (truncado em 255 caracteres)
- [ ] `POST /api/contas/redefinir-senha { token, senha }` → **204**: valida a senha (8 a 72), consome o token
      `RECUPERACAO`, grava o hash, `tokenContaService.invalidar(usuario)` (nenhum outro link vale), derruba todas as
      sessões (`SessaoService.encerrarTodas`), limpa o bloqueio de login da conta (`ChaveLogin.chavesDaConta` +
      `TentativasLogin.limpar`, para a pessoa conseguir entrar logo em seguida), `AuditLog` `SENHA_REDEFINIDA`.
      Não abre sessão. Dispara o e-mail de senha alterada (cartão 4)
- [ ] `POST /api/contas/recuperacao/detalhes { token }` → `{ primeiroNome }`, só para a tela não pedir senha para
      um link já morto (404 genérica / 410 vencido, igual ao cartão 2)
- [ ] E-mail `recuperacao` (`templates/email/recuperacao.{html,txt}`): `assunto`, `nome`, `prazo` ("1 hora"),
      `link` = `servio.front-url` + `/redefinir-senha#` + token, aviso "se não foi você, ignore este e-mail"
- [ ] Conta desativada é tratada como inexistente (mesma 202, sem e-mail)

Cuidados com testes existentes:

- [ ] `LoginIT`: o rate limit por conta compartilha `TentativasLogin` (estático no contexto de teste): quem
      redefine a senha no teste precisa de IP e e-mail próprios, como já fazem os testes de soma por conta
- [ ] `EmailTemplatesTest`: incluir `recuperacao`
- [ ] `AutorizacaoIT`/`CsrfIT`: as rotas públicas novas exigem o cabeçalho CSRF como as do convite

Testes novos — `RecuperacaoIT`:

- [ ] Pedido para e-mail existente e para e-mail inexistente: mesmo status e **mesmo corpo**; só o existente
      gera uma linha em `email_saida` (`recuperacao`)
- [ ] Pedido por telefone (formatos diferentes do mesmo número) cai na mesma conta e vai para o e-mail dela
- [ ] 4º pedido na mesma hora: 202, mas nenhum e-mail novo; depois de 1 hora de relógio volta a enviar
- [ ] Redefinir troca a senha: a antiga dá 401, a nova entra; sessão aberta antes da redefinição dá 401 em `/api/me`
- [ ] Token usado 2 vezes: 404; vencido (61 minutos): 410; segundo pedido mata o link do primeiro
- [ ] Conta bloqueada por rate limit de login (5 falhas): depois de redefinir, o login entra sem esperar 15 minutos
- [ ] Conta ainda não ativada e conta desativada: 202 e nenhum e-mail
- [ ] Nenhum log contém o token nem a senha nova

### Cartão 4 — E-mail de senha alterada e "Proteger minha conta"

Problema: se alguém trocar a senha de uma conta (ou a pessoa nem lembrar), ela precisa ser avisada e ter uma saída
rápida. Decisão já tomada: **sem cidade** no e-mail (exigiria serviço externo de IP); só o tipo de aparelho.

Ler: `service/MeService` (`trocarSenha`), `service/UsuarioService` (`atualizar`, troca de senha por PADRE/ADMIN),
`service/conta/*` (cartões 1 a 3), `security/SessaoService`, `controller/MeController`, `service/AuditLogService`,
`config/EmailTemplateConfig`, `templates/email/*`.

- [ ] `AparelhoUsuario` (`util`, sem Spring): `descrever(String userAgent)` → texto curto como `"Celular
      Android"`, `"Celular iPhone"`, `"Computador Windows"`, `"Computador Mac"`, `"Tablet"`, `"aparelho
      desconhecido"` (user-agent nulo, vazio ou fora do padrão). Sem biblioteca nova; não guarda nem loga o
      user-agent, IP nem localização
- [ ] `ContaAvisoService` (`service/conta`): `avisarSenhaAlterada(usuario, userAgent)` emite token `PROTECAO` (7
      dias) e enfileira o e-mail `senha-alterada` com `assunto`, `nome`, `quando` (data e hora no fuso
      `servio.fuso`), `aparelho` e `link` = `servio.front-url` + `/proteger-conta#` + token. **Sem cidade, sem IP.**
      Chamado em: `MeService.trocarSenha`, `redefinir-senha` (cartão 3) e `UsuarioService.atualizar` quando a
      senha foi trocada por PADRE/ADMIN (nesse caso `aparelho` = o do administrador não faz sentido: usar o texto
      "alteração feita pela coordenação da paróquia", ver Pendente/decisões). **Não** dispara na ativação (a
      senha inicial é ato da própria pessoa, que acabou de abrir o link do e-mail)
- [ ] `MeController.trocarSenha` passa o cabeçalho `User-Agent` para o service (único dado da requisição que
      entra no aviso)
- [ ] `POST /api/contas/protecao/detalhes { token }` → `{ primeiroNome }` e `POST /api/contas/protecao { token }`
      → **204** (públicas, `LimiteContaPublica`, 404 genérica / 410 vencido). A proteção:
  - [ ] consome o token `PROTECAO`, **zera a senha** (`senha NULL`, mantém `ativado_em`: é "conta protegida", não
        "conta nova"), invalida todos os tokens pendentes e derruba todas as sessões
  - [ ] emite token `RECUPERACAO` (1 hora) e enfileira o e-mail `recuperacao` para o e-mail da conta, para a
        pessoa definir uma senha nova por um caminho que o invasor não tem (se ele só trocou a senha, não tem
        acesso à caixa de e-mail)
  - [ ] `AuditLog` `CONTA_PROTEGIDA`
- [ ] Depois da proteção, login com a senha antiga ou qualquer outra dá a 401 genérica (conta sem senha, como no
      cartão 2); o `POST /api/contas/ativar` **não** serve (a conta já tem `ativado_em`: 404 genérica)

Cuidados com testes existentes:

- [ ] `UsuarioContaIT` (testes de `POST /api/me/senha`, troca de senha por PADRE/ADMIN) continuam passando, mas
      agora cada troca grava uma linha `senha-alterada` em `email_saida`: onde o IT não é `@Transactional`,
      `@MockitoBean Notificador` (como `JobsIT`); onde é, o `afterCommit` nunca roda e basta consultar a tabela
- [ ] `EmailTemplatesTest`: incluir `senha-alterada` e conferir que o texto **não** contém "cidade", IP nem
      localização
- [ ] `SessaoIT`: continua passando (a troca de senha já derrubava as sessões)

Testes novos — `SenhaAlteradaIT` e `AparelhoUsuarioTest` (unitário):

- [ ] `AparelhoUsuarioTest`: user-agents reais de iPhone, Android, Windows, Mac e iPad viram o texto certo; nulo,
      vazio e lixo viram "aparelho desconhecido"
- [ ] Trocar a senha por `POST /api/me/senha` com `User-Agent` de iPhone: uma linha `senha-alterada` com
      `aparelho` "Celular iPhone", `link` terminando em `/proteger-conta#<token>`, **sem** a senha em nenhuma
      variável e sem chave de cidade/IP
- [ ] Redefinição por link e troca por PADRE/ADMIN também enfileiram o aviso; ativação **não**
- [ ] Proteger: o token funciona uma vez; depois, sessão aberta dá 401, senha antiga dá 401, uma linha
      `recuperacao` aparece na fila, e a redefinição por esse link devolve o acesso
- [ ] Token `PROTECAO` vencido (7 dias + 1 minuto): 410; token de outro tipo na rota de proteção: 404
- [ ] Nenhum log contém o token

### Cartão 5 — Mudança de papel encerra as sessões

Problema: a sessão de quem é só membro dura 60 dias e a de quem tem qualquer papel de gestão, 7 (cartão 5 da
sprint 1), decidido no login. Quem ganha ou perde um papel continua com a duração antiga, e um papel removido
não derruba nada. Hoje só mudança de perfil, e-mail, senha ou desativação do usuário encerram sessões
(`UsuarioService`). Decisão: qualquer mudança de papel numa pastoral encerra todas as sessões da pessoa afetada.

Ler: `service/UsuarioPastoralService` (`criar`/`atualizar`/`desativar`, herdados de `CrudService`),
`controller/UsuarioPastoralController`, `security/{SessaoService, SessaoDuracao, LoginHandlers}`,
`service/UsuarioService` (`atualizar`, `desativar` — o padrão atual), testes `seguranca/SessaoIT`
(`rebaixarPerfilDerrubaASessao`), `pastoral/UsuarioPastoralIT`.

- [ ] `SessaoService.encerrarTodasAposCommit(email)`: se há transação ativa, registra `afterCommit` que chama
      `encerrarTodas(email)`; sem transação, encerra na hora. (Hoje `UsuarioService` encerra dentro da transação:
      se ela desfizer depois, a pessoa já foi deslogada à toa. Esse método novo evita isso; **não** migrar os
      usos atuais nesta sprint, só o novo)
- [ ] `UsuarioPastoralService`: criar uma participação, trocar o papel e desativar uma participação chamam
      `encerrarTodasAposCommit` do e-mail da pessoa **afetada** (a dona da participação, não quem fez a
      alteração). Não encerra no cadastro de membro novo do cartão 2 (a conta ainda não tem sessão), mas o
      mesmo código serve: encerrar sessão inexistente é um no-op
- [ ] Nada mais muda: a autorização por papel continua sendo consultada no banco a cada chamada
      (`PastoraisPermissao`), então o efeito imediato desta mudança é a duração da sessão e o fim de
      "sessões antigas", não a segurança em si
- [ ] `MeResponseDTO` não muda. O front, ao receber 401 em `/api/me`, já volta para a tela Entrar

Cuidados com testes existentes:

- [ ] `UsuarioPastoralIT`, `PermissaoPorPapelIT`, `EntrePastoraisIT`: não criam sessão; continuam passando
- [ ] `SessaoIT.rebaixarPerfilDerrubaASessao` continua passando

Testes novos — em `SessaoIT`:

- [ ] Membro com sessão aberta é promovido a `COORDENADOR` (pelo padre): `/api/me` com o cookie antigo dá 401, o
      novo login dura 7 dias (`spring_session.max_inactive_interval`)
- [ ] Coordenador rebaixado a `MEMBRO` e participação removida: sessão antiga cai; novo login volta a 60 dias
      (só membro) ou segue em 7 (outro papel de gestão em outra pastoral)
- [ ] A sessão de **outra** pessoa não cai; a de quem fez a alteração também não (a menos que seja a própria)
- [ ] Alteração recusada (409 de papel duplicado, 403, 404): ninguém é deslogado (efeito só no commit)

### Cartão 6 — Foto de perfil (opcional, com limite de tamanho)

Problema: a casca nova do front tem avatar, e o `GET /api/me` ainda não tem `fotoUrl` (ficou fora da sprint 1).
Decisão: a foto é **opcional**; sem ela o front mostra as iniciais.

Ler: `controller/MeController`, `service/MeService`, `model/dto/MeResponseDTO`, `config/SecurityConfig`
(`anyRequest().denyAll()`, cabeçalhos), `exception/GlobalHandleException`, `application.properties`,
`security/SomenteLeituraFilter` (contas de demonstração não escrevem), `model/TenantEntity`.

- [ ] `V19__usuario_foto.sql`: tabela `usuario_foto` — `usuario_id BIGINT PRIMARY KEY REFERENCES usuario`,
      `conteudo BYTEA NOT NULL`, `tipo_conteudo VARCHAR(20) NOT NULL` (`CHECK = 'image/jpeg'`),
      `atualizado_em TIMESTAMP NOT NULL DEFAULT now()`. Guardada no banco porque a hospedagem ainda não está
      decidida (ver Pendente/decisões); a imagem final tem poucas dezenas de KB
- [ ] Entidade `UsuarioFoto` (não é `TenantEntity`; a paróquia vem do `Usuario` dono) e `UsuarioFotoRepository`
- [ ] Limites configuráveis: `servio.foto.max-bytes=2097152` (2 MB), `servio.foto.lado-maximo=512` e
      `spring.servlet.multipart.max-file-size=2MB`/`max-request-size=3MB` (para o Tomcat nem aceitar o corpo
      grande); `GlobalHandleException` trata `MaxUploadSizeExceededException` como **413** "A foto pode ter no
      máximo 2 MB"
- [ ] `FotoPerfilService` (`service`): valida **pelo conteúdo**, não por nome nem `Content-Type` do cliente:
  - [ ] só JPEG e PNG (leitura de bytes iniciais, `ImageIO.getImageReaders`); GIF, WebP, SVG, PDF, HTML
        renomeado: 400 "Envie uma foto JPEG ou PNG"
  - [ ] confere largura x altura **antes** de decodificar o bitmap (teto de 6000 x 6000 pixels, contra imagem
        minúscula em bytes e gigante em pixels): acima disso 400 "Imagem grande demais"
  - [ ] decodifica, recorta ao quadrado central, reduz para no máximo 512 x 512 e **recodifica em JPEG**
        (qualidade 0,85): remove EXIF (localização do celular), perfil de cor e qualquer conteúdo escondido
  - [ ] grava (ou troca) a linha em `usuario_foto`; `AuditLog` `FOTO_ALTERADA` (sem o conteúdo)
- [ ] `PUT /api/me/foto` (`multipart/form-data`, parte `arquivo`) → **200** com o mesmo corpo do `GET /api/me`;
      `DELETE /api/me/foto` → **204** (sem foto não é erro). CSRF continua exigido. A rota
      `/api/me/**` já é `authenticated`
- [ ] `GET /api/fotos/{usuarioId}` (novo matcher `authenticated` para `GET` no `SecurityConfig`, antes do
      `denyAll`; **não** abrir `/api/usuarios/**`): devolve `image/jpeg` só se o dono for **da mesma paróquia**
      de quem pede e ativo; senão 404, igual a "sem foto". `Cache-Control: private, max-age=86400`, `ETag` =
      `atualizado_em` (304 em `If-None-Match`), `X-Content-Type-Options: nosniff`
- [ ] `MeResponseDTO` ganha `String fotoUrl` (`null` sem foto; com foto, `/api/fotos/{id}?v=<atualizado_em em
      segundos>` para o navegador buscar de novo ao trocar). `MembroPastoralDTO` também ganha `fotoUrl` (as
      iniciais viram avatar na lista de membros); os demais DTOs de lista ficam como estão (ver
      Pendente/decisões)
- [ ] Contas de demonstração (`SomenteLeituraFilter`) continuam sem poder gravar: o `PUT`/`DELETE` entram na regra
      que já bloqueia escrita

Cuidados com testes existentes:

- [ ] `MeIT`: o JSON ganha `fotoUrl` (`null`); os asserts atuais valem; acrescentar `fotoUrl` ao caso do `GET`
- [ ] `OpenApiIT`: acrescentar `$.paths./api/me/foto` e `$.paths./api/fotos/{usuarioId}`; depois do cartão,
      rodar o OpenAPI (`OPENAPI_ENABLED=true`) para o front rodar `npm run gen:api` (o cliente gerado é
      commitado: rodar com Postgres no ar, ver `frontend/orval.config.ts`)
- [ ] `MassAssignmentIT`, `AutorizacaoIT`: rotas novas entram (sem login 401; sem CSRF 403)
- [ ] `VazamentoIT`/`TenantIsolamentoIT`: a foto de outra paróquia dá 404, como o resto

Testes novos — `FotoPerfilIT` e `FotoPerfilServiceTest` (imagens geradas com `ImageIO` no próprio teste; nenhum
arquivo binário no repositório):

- [ ] PNG 1200 x 800 válido: 200, `fotoUrl` preenchida; `GET /api/fotos/{id}` devolve `image/jpeg` de 512 x 512
- [ ] Imagem com metadados EXIF/texto: depois do envio os bytes guardados não contêm "Exif"
- [ ] Arquivo acima de 2 MB: 413; texto/HTML com extensão `.png`: 400; GIF: 400; imagem de 20000 x 20000 pixels
      (poucos KB de PNG): 400 sem estourar a memória do teste
- [ ] `DELETE`: `fotoUrl` volta a `null` e o `GET` dá 404; `DELETE` sem foto: 204
- [ ] Sem login: 401; de outra paróquia: 404; `If-None-Match` igual ao `ETag`: 304
- [ ] Conta de demonstração: `PUT` recusado como qualquer escrita

## Contrato combinado (rotas novas; ver `sprint-0.md`, seção 5)

> O "Mapa de endpoints" do front não estava disponível quando este documento foi escrito: **nomes e formatos
> abaixo são a proposta do back-end** e precisam ser confirmados com o colaborador antes do cartão 2.

- [ ] `POST /api/pastorais/{id}/membros` `{ nome, email, papel? }` → 201 `{ usuarioId, usuarioPastoralId,
      ativacaoEnviadaPara }`; 409 e-mail em uso; 403/404 como as rotas de pastoral
- [ ] `POST /api/pastorais/{id}/membros/{usuarioId}/reenviar-ativacao` → 204; 422 já ativada; 429 uma por hora
- [ ] `GET /api/pastorais/{id}/membros`: `ativada` e `fotoUrl` novos em cada membro
- [ ] Públicas (sem login, com CSRF): `POST /api/contas/ativacao/detalhes`, `POST /api/contas/ativar`,
      `POST /api/contas/recuperacao`, `POST /api/contas/recuperacao/detalhes`,
      `POST /api/contas/redefinir-senha`, `POST /api/contas/protecao/detalhes`, `POST /api/contas/protecao`.
      Erros: 404 token inválido/usado, 410 vencido, 400 senha fora de 8–72, 429 limite
- [ ] Links nos e-mails: `/ativar#token`, `/redefinir-senha#token`, `/proteger-conta#token` (fragmento)
- [ ] `GET /api/me`: `fotoUrl`; `PUT /api/me/foto` (multipart `arquivo`), `DELETE /api/me/foto`;
      `GET /api/fotos/{usuarioId}` (imagem)
- [ ] Mensagens de erro que o front vai mostrar (link inválido, link vencido, senha curta, e-mail em uso, foto
      grande demais, formato de foto não aceito, muitas tentativas)

## Fechamento da sprint

- [ ] Atualizar `Features/features-implementadas.md` e `Features/erros-conhecidos.md` com o que mudou
- [ ] Atualizar `Features/checklist-seguranca.md` (rotas públicas novas, tokens em fragmento de URL, foto
      recodificada) e o `runbook-piloto.md` (como reenviar a ativação; o que fazer se um e-mail não chegou)
- [ ] Resumo por cartão: o que mudou, testes novos, e qualquer coisa que ficou pendente ou decidida diferente
      do planejado (com o motivo)
- [ ] Conferir o "Pronto quando": cadastrar um membro de teste, abrir o link no e-mail (Mailpit em dev), definir
      a senha e entrar, sem que a senha tenha passado por mais ninguém

## Pendente/decisões

Dúvidas reais deste planejamento. Nenhuma foi decidida por palpite: a coluna "padrão proposto" é o que o código
faz **se ninguém decidir diferente**, para não travar o cartão.

| Dúvida | Padrão proposto | Cartão |
|---|---|---|
| Textos dos e-mails de ativação, recuperação e senha alterada (rascunhos no protótipo, fileira E-mails; revisão até 09/11) | O código entrega templates com texto provisório simples; trocar só os `.html`/`.txt` depois, sem mexer em Java | 2, 3, 4 |
| Domínio e remetente (SPF, DKIM, DMARC) até 08/11 | Sem eles, e-mail cai no spam. Bloqueia o "pronto quando" na prática, não o código | todos |
| Ativar a conta já abre a sessão (login automático)? | Não: devolve 204 e o front leva para Entrar. Menos superfície (o token de e-mail não vira sessão) e mantém um único ponto de criação de sessão (`LoginHandlers`) | 2 |
| Conta pendente (nunca ativada) pede "esqueci a senha": reenvia a ativação ou ignora? | Ignora (202 sem e-mail); quem reenvia é a coordenação. Reenviar sozinho abre mais um caminho de spam para a caixa de quem foi cadastrado por engano | 3 |
| Como a conta sem senha falha no login | Hash BCrypt constante de senha descartada, para a 401 e o tempo serem iguais aos de senha errada | 2 |
| Prazo do link de "Proteger minha conta" | 7 dias | 4 |
| "Proteger minha conta" zera a senha e manda recuperação por e-mail. Se o invasor também tem a caixa de e-mail, não ajuda | Aceito: é o limite do canal único (e-mail). Segundo canal (telefone/SMS) só se houver serviço de SMS | 4 |
| Texto do e-mail quando a senha foi trocada por PADRE/ADMIN (não há "aparelho" da pessoa) | "Alteração feita pela coordenação da paróquia", sem aparelho | 4 |
| Aviso de senha alterada também na ativação? | Não | 4 |
| Mudança de papel: encerrar sessão em **qualquer** mudança ou só quando a duração (60 x 7 dias) muda? | Qualquer mudança (é o texto do escopo); simples e previsível. O custo é deslogar membro quando só ganha outro papel de membro | 5 |
| O front precisa de aviso "seu acesso mudou, entre de novo" ao cair no 401? | Não é back-end; hoje volta para Entrar sem explicação | 5 |
| Onde guardar a foto: banco, disco ou serviço de arquivos (hospedagem só é decidida até 01/02) | Banco (`BYTEA`), redimensionada para 512 px (algumas dezenas de KB). Trocar de destino depois fica atrás do `FotoPerfilService` | 6 |
| Formatos aceitos: WebP e HEIC (celulares) | Só JPEG e PNG: o `ImageIO` do JDK não lê WebP/HEIC sem biblioteca extra. Foto HEIC do iPhone costuma sair como JPEG ao enviar pelo navegador | 6 |
| Quais outras telas precisam de `fotoUrl` (escala, painel, agenda, sininho) | Só `GET /api/me` e `GET /api/pastorais/{id}/membros` nesta sprint; sem o "Mapa de endpoints" não dá para saber mais | 6 |
| `POST /api/usuarios` com senha: remover quando o front migrar para o cadastro sem senha? | Mantém como está nesta sprint; remoção em outra sprint, com teste cobrindo | 2 |
| O token puro fica em `email_saida.variaveis` (JSONB) até o e-mail sair ou falhar 5 vezes (a `FilaEmail` apaga depois). Quem lê o banco nessa janela vê links válidos | Aceito: é a mesma situação do convite; janela curta, `variaveis` apagada no envio | 2, 3, 4 |
| Cadastro de membro com telefone | Não (regra do cartão 3 da sprint 1: só a própria pessoa define pelo `PUT /api/me`) | 2 |
| Nomes das rotas e dos campos (sem o "Mapa de endpoints") | Proposta no "Contrato combinado"; confirmar com o colaborador antes de começar o cartão 2 | 2 a 6 |
| Conta pendente (cadastrada e nunca ativada) não expira: reserva o e-mail no sistema todo (e-mail é único entre paróquias) e quem recebe o cadastro de outra paróquia leva 409. Um coordenador pode "estacionar" e-mails de outras pessoas | Aceito nesta sprint, mitigado pelo limite de 20 cadastros por hora por coordenador. Proposta: job que desativa conta pendente depois de X dias (sugestão: 30) e libera o e-mail | 2 |
| `POST /api/pastorais/{id}/membros` com papel `COORDENADOR` por quem não é padre/ADMIN dá 403 sem checar se a pastoral está no alcance; para as demais, pastoral fora do alcance dá 404. Quem não é gestor distingue pastoral existente (403) de inexistente (404) dentro da própria paróquia | Aceito: mesmo comportamento do `UsuarioPastoralService.exigirGestor` de hoje; os ids são da própria paróquia | 2 |
| 404 x 410 nas rotas públicas de conta: o 410 (vencido) revela que o token existiu | Aceito: o token tem 256 bits (não se adivinha) e a mensagem precisa distinguir "vencido" para a tela pedir novo link | 1, 2 |

## Fora desta sprint

- [ ] Telas Entrar, Ativar conta, Esqueci a senha, e-mail de senha alterada (renderização), avatar e seletor de
      foto: trabalho do colaborador
- [ ] Apagar telefone, segundo canal de recuperação (SMS), migração dos usos antigos de `encerrarTodas` para
      `afterCommit`, WebP/HEIC

Ver também: `sprint-1.md`, `sprint-0.md`, `features-futuras.md`.
