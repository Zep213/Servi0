# Servio — Sprint 3

**Período:** 23/11 a 06/12/2026 · **Back-end:** Zep · **Front-end:** colaborador

Habilidades e membros. **Pronto quando:** o sorteio só escolhe quem tem a habilidade da vaga.

Stack: Spring Boot 4.1.1, Java 25, Maven wrapper, JPA/Hibernate, Lombok, MapStruct, Flyway, PostgreSQL 17,
Spring Session JDBC, Spring Security com `formLogin`, Thymeleaf para e-mails, Testcontainers nos ITs (`*IT`).

Detalhado a partir de `features-futuras.md` (seção "Sprint 3" e "Decisões pendentes") e do código que já está na
`main`; ver também `sprint-2.md` (de onde vêm o status de ativação e a foto) e `sprint-0.md`. Escopo desta sprint é
**só back-end**: as telas Membros (busca, filtros), Detalhe do membro e Adicionar pessoa são do colaborador (front).

## O que já existe (e o que falta)

Hoje a "habilidade" de uma pessoa já é um dado do sistema, só que **ninguém o usa**:

- `funcao` é o catálogo de funções de uma pastoral (`Funcao`: nome + pastoral), e `vaga` aponta para uma `Funcao`
- `usuario_funcao` (`UsuarioFuncao`: usuário + função) liga a pessoa às funções que ela sabe fazer. É o que o
  cronograma chama de **habilidade**. Só PADRE/ADMIN mexem nela (`/api/usuarios-funcoes/**` está em `CADASTROS`) e
  **nenhuma regra, sorteio ou tela a consulta**
- `ElegibilidadeService.avaliar(vaga)` avalia **todos** os membros ativos da pastoral da vaga contra o catálogo de
  regras (`RegraElegibilidade`: `UMA_VAGA_POR_CELEBRACAO`, `SEM_INDISPONIBILIDADE`, `INTERVALO_MINIMO`,
  `DOMINGOS_SEGUIDOS`). Não existe regra de habilidade: qualquer membro é candidato a qualquer vaga
- `EscalacaoService.sortear` ainda restringe o sorteio a quem tem o papel `MEMBRO` (`membrosSorteaveis`), por uma
  decisão provisória: coordenação, secretaria, tesouraria, técnico e redes sociais ficam de fora porque, sem
  habilidade, não dá para saber o que cada um sabe fazer
- O sorteio percorre as vagas **em ordem de id**. Com habilidades, isso pode gastar uma pessoa com duas
  habilidades numa vaga folgada e deixar sem ninguém a vaga mais apertada da mesma celebração

Esta sprint liga as pontas: habilidade vira regra do catálogo, o filtro provisório "só MEMBRO" sai, o sorteio
começa pela função mais apertada, e a lista de membros ganha o status de ativação que a tela precisa.

## Pré-requisitos (o que já precisa estar na `main`)

- [ ] Sprint 2 mergeada, em especial o cartão 2 (cadastro de membro sem senha: `Usuario.estaAtivada()`,
      `MembroPastoralDTO.ativada`) e o cartão 6 (foto de perfil: `fotoUrl`). O cartão 5 desta sprint usa os dois
- [ ] Sem migration prevista nesta sprint. Se alguma aparecer, a próxima livre é `V20` (`V17` a `V19` são da
      sprint 2; confirme na `main` antes de numerar)

## Regras de trabalho

- [ ] Antes de codar cada cartão: ler as classes citadas nele e mostrar o plano antes de começar
- [ ] Um branch por cartão, a partir do `main`: `feat/s3-habilidades-gestao`, `feat/s3-regra-habilidade`,
      `feat/s3-sorteio-todos-papeis`, `feat/s3-funcao-mais-apertada`, `feat/s3-membros-status`. Os cartões 3 e 4
      **dependem do 2** e empilham nele (ver o aviso no cartão 3); o PR diz de qual depende
- [ ] Commits pequenos, mensagem em português
- [ ] Migrations novas a partir de `V20` (nunca editar migration que já existe)
- [ ] `./mvnw verify` ao fim de cada cartão (precisa do Docker ligado); não passar para o próximo com teste vermelho
- [ ] Não mudar comportamento fora do que está descrito em cada cartão; o que parecer errado no caminho vai pra
      "Pendente/decisões" no fechamento, não é corrigido de lado
- [ ] Nunca logar token, senha nem corpo de e-mail. Telefone e e-mail completos de outras pessoas não vão para log
- [ ] Comentários e mensagens de erro em português, no mesmo estilo do código atual

## Escopo (5 cartões, nesta ordem)

### Cartão 1 — A coordenação gerencia as habilidades dos membros da própria pastoral

Problema: as habilidades (`usuario_funcao`) só podem ser criadas por PADRE/ADMIN, pelas rotas de cadastro
(`/api/usuarios-funcoes/**` está em `CADASTROS`). Com a regra de habilidade (cartão 2) valendo, quem marca "esta pessoa
sabe fazer som" é o coordenador da pastoral, e hoje ele recebe 403. Além disso, o serviço atual tem um defeito que
a tela de habilidades (marcar, desmarcar, marcar de novo) expõe.

Ler: `service/UsuarioFuncaoService`, `controller/UsuarioFuncaoController`, `repository/UsuarioFuncaoRepository`
(`existsByUsuarioIdAndFuncaoId`), `model/{UsuarioFuncao, Funcao}`, `service/UsuarioPastoralService` (`exigirGestor`: o
padrão de 404 x 403 por pastoral), `security/PastoraisPermissao`, `config/SecurityConfig` (`CADASTROS`,
`GESTAO_POR_PASTORAL`), `service/FuncaoService`, migration `V2` (índice `uq_usuario_funcao_ativo`),
`pastoral/{UsuarioPastoralIT, PermissaoPorPapelIT, EntrePastoraisIT}`.

- [ ] **Corrigir a unicidade:** `existsByUsuarioIdAndFuncaoId` não filtra `is_active`, então, depois de desmarcar uma
      habilidade (desativação lógica), marcar de novo dá 409 "Usuário já possui esta função", embora o banco
      (`uq_usuario_funcao_ativo`, parcial em `is_active`) deixe. Trocar por `existsByUsuarioIdAndFuncaoIdAndActiveTrue`
      (e, no `atualizar`, ignorar o próprio id, como `UsuarioService.validar` faz com o e-mail)
- [ ] **Quem pode marcar/desmarcar:** PADRE e ADMIN continuam como hoje. Passa a poder também o **coordenador da
      pastoral dona da função** (decisão: só coordenador, o mesmo gate de `exigirGestor` para papéis comuns; vice
      não, ver Pendente/decisões), e só para pessoa que **tem papel nessa pastoral**. Regras:
  - [ ] `GET /api/usuarios-funcoes` (listagem geral paginada) e `PUT`/`DELETE`/`POST`: o gate grosso do
        `SecurityConfig` (`CADASTROS` = ADMIN/PADRE) sai só para `POST`, `PUT` e `DELETE` de `/api/usuarios-funcoes/**`,
        que passam a `authenticated()` com a regra fina no service (como `/api/usuarios-pastorais/**`, que está em
        `GESTAO_POR_PASTORAL`). A listagem geral (`GET`) continua como está
  - [ ] No service: função de outra paróquia ou fora do alcance dá 404; coordenador de **outra** pastoral dá 404
        (pastoral fora do alcance) ou 403 (pastoral visível, sem ser coordenador), igual a
        `UsuarioPastoralService.exigirGestor`; pessoa sem papel na pastoral da função: 422 "Esta pessoa não faz parte da
        pastoral desta função"
  - [ ] `AuditLog` `HABILIDADE_ALTERADA` (marcar/desmarcar), com função e pessoa por id, sem e-mail
- [ ] **Habilidades de um membro numa só chamada:** `GET /api/pastorais/{pastoralId}/membros/{usuarioId}/habilidades` →
      `[{ funcaoId, nome, marcada }]` (todas as funções da pastoral, com `marcada` para as que a pessoa tem) e
      `PUT` do mesmo caminho com `{ funcaoIds: [...] }` que **troca o conjunto** (marca as que faltam, desmarca as que
      sobram) na mesma transação. É o que a tela "Detalhe do membro" usa; as rotas simples ficam para quem as já
      usa. Mesma permissão do bloco acima; `fail-on-unknown-properties` vale. Função que não é da pastoral: 404, nada
      gravado. `GET` pode ser visto por qualquer um que veja a pastoral (a lista é do catálogo da pastoral, sem dado
      pessoal); só o coordenador altera
- [ ] Rotas novas entram no `GESTAO_POR_PASTORAL` (`/api/pastorais/*/membros/**` já está) sem abrir nada além

Cuidados com testes existentes:

- [ ] `AutorizacaoIT`/`PermissaoPorPapelIT`: se algum teste espera 403 do coordenador em `/api/usuarios-funcoes`,
      atualizar para o novo comportamento (coordenador da pastoral da função passa; os demais seguem barrados)
- [ ] `MassAssignmentIT`: acrescentar o corpo novo (`funcaoIds`) com campo a mais → 400

Testes novos — `HabilidadeGestaoIT`:

- [ ] Coordenador marca uma habilidade de um membro da própria pastoral: 201, uma linha `usuario_funcao`, `AuditLog`
- [ ] Marcar, desmarcar e marcar **de novo** a mesma habilidade: terceiro passo dá 201 (hoje daria 409)
- [ ] Marcar duas vezes seguidas sem desmarcar: 409
- [ ] Coordenador de outra pastoral: 404; vice e membro da própria pastoral: 403; padre e admin: passam
- [ ] Pessoa sem papel na pastoral da função: 422; função de outra paróquia: 404
- [ ] `PUT .../habilidades` troca o conjunto numa transação (uma função inválida na lista: 404 e **nada** muda)
- [ ] `GET .../habilidades` mostra todas as funções da pastoral e `marcada` certo

### Cartão 2 — Regra `HABILIDADE` no catálogo de elegibilidade

Problema: qualquer membro da pastoral é candidato a qualquer vaga. A pessoa que só sabe ler (leitor) aparece para
a vaga de som e pode ser sorteada para ela.

Ler: `service/escalacao/regra/{RegraElegibilidade, ContextoElegibilidade, UmaVagaPorCelebracaoRegra,
SemIndisponibilidadeRegra}`, `service/escalacao/{ElegibilidadeService, ConfiguracaoPastoralService, CandidatoAvaliado}`,
`controller/RegraElegibilidadeController` (`/api/regras/catalogo`), `model/{UsuarioFuncao, Vaga, PastoralConfig}`,
`repository/UsuarioFuncaoRepository`, `service/escalacao/EscalacaoService` (`escalar` com `forcar`, `candidatos`),
testes `pastoral/{SorteioIT, AlocacaoRegrasIT, EscalacaoManualIT, UmaVagaPorCelebracaoRegraTest}` e
`pastoral/{CenarioParoquia, CenarioEscalacao}`.

- [ ] `HabilidadeRegra` (`service/escalacao/regra`, `@Component`): `codigo()` = `"HABILIDADE"`, `descricao()` = "A pessoa
      precisa ter a habilidade (função) da vaga", `padroes()` = `Map.of()`. `impedimento(candidato, contexto, ...)`:
      se não existe `usuario_funcao` **ativa** entre o candidato e a função da vaga, devolve "Não tem a habilidade desta
      vaga". É só criar a classe: o Spring a injeta em `List<RegraElegibilidade>` e `ElegibilidadeService`,
      `/api/regras/catalogo` e a tela de configuração da pastoral já a enxergam
- [ ] `UsuarioFuncaoRepository.existsByUsuarioIdAndFuncaoIdAndActiveTrue` (usado aqui e no cartão 1). **Custo:** a
      regra consulta uma vez por candidato (como as outras, "dívida conhecida" do `ElegibilidadeService`); para um
      membro por vaga são poucas dezenas de consultas por sorteio, aceitável. Se der para evitar sem complicar, a regra
      pode pedir ao contexto o conjunto de funções do candidato já carregado em lote; não é obrigatório nesta sprint
- [ ] **Liga por padrão** em todas as pastorais (como as outras regras: `ConfiguracaoPastoralService.ativa` devolve
      `true` quando não há linha em `pastoral_config`). Uma pastoral pode desligar `HABILIDADE` na configuração, pelo
      mesmo `PUT /api/pastorais/{id}/config/HABILIDADE` das demais regras. Ver Pendente/decisões (piloto sem habilidades
      cadastradas)
- [ ] Efeito em todo lugar que usa o catálogo, sem código novo: `GET /api/vagas/{id}/candidatos` mostra quem não tem
      a habilidade como impedido; `POST /api/vagas/{id}/escalar` e `POST /api/alocacoes` (escalação manual) barram com **422** (`RegraNegocioException`,
      o mesmo das demais regras) quem não tem a habilidade, e o coordenador pode forçar (`forcar`, só ele); o sorteio ignora quem tem impedimento

Cuidados com testes existentes — **este é o cartão que mais mexe nos testes**:

- [ ] Nenhum IT ou cenário de hoje cria `usuario_funcao`, e vários ITs escalam pessoas que **nem são da pastoral**
      (`PermissaoPorPapelIT.candidatoFresco()`, `AlteracaoPendenteIT`, `AlocacaoRegrasIT`, `ConviteRespostaIT` criam
      `new Usuario()` soltos; `SorteioIT` cria membros e cargos no meio dos testes). `CenarioParoquia` tem 22 pontos de
      construção. Dar habilidade a cada membro no cenário **não basta** e semear cada fábrica, sem Docker local, é
      arriscado. **Decisão (aprovada no PR 38): interruptor de implantação**
      `servio.escalacao.habilidade-ligada-por-padrao` (`true` em produção; `RegraElegibilidade.ligadaPorPadrao()` e o
      `ConfiguracaoPastoralService.ativa`/`PastoralConfigService.efetivas` o respeitam quando a pastoral nunca
      configurou). Só o `application-test.properties` o põe em `false`, então nenhum IT antigo muda; a regra é testada
      **ligada** em `HabilidadeRegraIT` (`@TestPropertySource`) e em `HabilidadeRegraTest`. `CenarioParoquia` ganha
      `habilitar(...)` e `habilitarTodos(...)` para os testes que precisam de habilidade
- [ ] `ConfiguracaoPastoralServiceTest`/`/api/regras/catalogo`: o catálogo ganha mais uma regra; acertar os asserts
      de tamanho/lista
- [ ] Testes de unidade das regras existentes não mudam

Testes novos — `HabilidadeRegraTest` (unitário, repositório mockado) e casos em `AlocacaoRegrasIT`/`SorteioIT`:

- [ ] `HabilidadeRegraTest`: com habilidade ativa → sem impedimento; sem linha → "Não tem a habilidade desta vaga";
      com a linha **desativada** → impedido
- [ ] `candidatos` de uma vaga de som: quem só tem a habilidade de leitura aparece com o motivo; quem tem som, elegível
- [ ] `POST /api/vagas/{id}/escalar` de quem não tem a habilidade: 422 com o motivo; com `forcar` pelo coordenador: 201;
      pelo vice com `forcar`: 403 (a regra de forçar não muda)
- [ ] Sorteio só convida quem tem a habilidade; se ninguém tem, a vaga volta em `incompletas` e sai o
      `VagaSemElegiveisEvent` (aviso ao coordenador)
- [ ] Pastoral que desliga `HABILIDADE` na configuração volta a sortear qualquer membro
- [ ] Desmarcar a habilidade (cartão 1) tira a pessoa do sorteio seguinte

### Cartão 3 — Tirar o filtro "só MEMBRO" do sorteio

> **Só entra depois do cartão 2.** Sem a regra de habilidade, tirar o filtro faria o sorteio considerar
> coordenador, secretário, tesoureiro, técnico e redes sociais para qualquer vaga. Empilhar neste cartão no branch
> do cartão 2 e **não mergear antes dele**.

Problema: o sorteio só considera quem tem o papel `MEMBRO` (`EscalacaoService.membrosSorteaveis`). Coordenação,
secretaria, tesouraria e redes sociais ficam de fora mesmo quando sabem e querem fazer a função. Com a regra de
habilidade, o filtro é redundante: quem não tem a habilidade já está impedido.

Ler: `service/escalacao/EscalacaoService` (`sortear`, `membrosSorteaveis`, `RECUSARAM`),
`repository/UsuarioPastoralRepository` (`findUsuariosPorPapel`, `findMembrosAtivosComUsuario`),
`service/escalacao/ElegibilidadeService.avaliar` (já avalia **todos** os papéis da pastoral),
`model/enumerated/PapelPastoral`, `pastoral/SorteioIT` (`soMembroESorteadoCoordenacaoECargosFicamDeFora`,
`tecnicoSorteiaComoOVice`, `redesSociaisNaoSorteia`, `viceSorteia`).

- [ ] Remover `membrosSorteaveis` e o `Map<Long, Set<Long>> membrosPorPastoral` do `sortear`, e o
      `.filter(c -> sorteaveis.contains(...))`: os candidatos passam a ser `elegibilidadeService.avaliar(vaga)`
      (menos os que recusaram a vaga), como já é em `candidatos` e na escalação manual
- [ ] `findUsuariosPorPapel` deixa de ser usado pelo sorteio; se nenhum outro código o usar, remover a consulta (e o
      teste, se houver). **Não** mexer em `findMembrosAtivosComUsuario`
- [ ] Comportamento resultante (a documentar no Javadoc do `sortear`): **todo papel** da pastoral pode ser sorteado,
      desde que tenha a habilidade da vaga e passe nas demais regras. Quem faz o papel de "só cargo" (técnico,
      redes sociais) e não tem habilidade nenhuma marcada continua fora do sorteio, pelo cartão 2. A decisão "Técnico
      também como habilidade na escala?" (padrão: **só como cargo**) não muda: não nasce função "Técnico" no
      catálogo por causa deste cartão
- [ ] Quem **pode sortear** não muda (`exigirGestor`: coordenador ou vice; técnico passa como vice)

Cuidados com testes existentes:

- [ ] `SorteioIT.soMembroESorteadoCoordenacaoECargosFicamDeFora` **deixa de valer** como está: reescrever. Novo sentido:
      "papel sem habilidade da vaga não é sorteado; com habilidade, é" (virou
      `quemNaoTemAHabilidadeNaoEhSorteadoSejaQualForOPapel` + `coordenacaoECargosComAHabilidadeSaoSorteados...`)
- [ ] `redesSociaisNaoSorteia`, `tecnicoSorteiaComoOVice` e `viceSorteia` testam quem pode **chamar** o sorteio (redes
      sociais 403; técnico e vice passam), não quem é sorteado: **continuam como estão**
- [ ] `NotificacaoIT.vagaQueFicouAbertaAvisaOCoordenador` (vaga de 50 sorteada com a regra desligada) continua válida: com o
      filtro fora há mais candidatos, mas a vaga segue incompleta
- [ ] **O `SorteioIT` passa a rodar com a regra `HABILIDADE` ligada** (`@TestPropertySource`) e a semear habilidade para
      as 7 pessoas que hoje são "os sorteáveis" da Pascom (`membro1`, `membro2` e os 5 extras) nas funções que as vagas
      usam. Sem isso, tirar o filtro "só MEMBRO" faria **todos** os papéis da Pascom virarem candidatos (regra
      desligada nos ITs) e as contagens do teste (7 convidados, `isSubsetOf(membrosPascom)`) quebrariam

Testes novos — em `SorteioIT`:

- [ ] Coordenador com a habilidade da vaga **é** sorteado; sem a habilidade, não
- [ ] Secretário, tesoureiro, técnico e redes sociais: mesma regra (com habilidade entram, sem não entram)
- [ ] Um membro que só tem papel `MEMBRO` e a habilidade continua sorteável (nada regride)
- [ ] Quem recusou a vaga continua fora do sorteio seguinte

### Cartão 4 — Sortear primeiro a função mais apertada

> Empilha no cartão 2 (precisa da regra de habilidade para existir "apertada"). Independente do cartão 3.

Problema: `EscalacaoService.sortearCelebracao` sorteia as vagas da pastoral em **ordem de id**. Com habilidades, uma
pessoa pode servir em mais de uma função. Se a vaga de id menor é a folgada (muitos candidatos) e a de id maior é a
apertada (poucos), a pessoa rara pode ser usada na vaga folgada e a apertada fica vazia, mesmo havendo
solução que preenche as duas. Como `UmaVagaPorCelebracaoRegra` impede a mesma pessoa em duas vagas da celebração,
a ordem importa.

Exemplo: vaga **Leitura** (1 vaga, id 10) com 3 candidatos, vaga **Som** (1 vaga, id 11) com 1 candidato, e a única
pessoa que sabe som também sabe ler. Em ordem de id, o sorteio pode dar a Leitura para ela e deixar Som vazia.

Ler: `service/escalacao/EscalacaoService` (`sortearCelebracao`, `sortear`, `travar`, `ocupantes`),
`service/escalacao/SorteioService`, `service/escalacao/ElegibilidadeService.avaliar`, `model/Vaga`,
`repository/VagaRepository` (`findByIdParaEscalarComLock`), `pastoral/{SorteioIT, SorteioServiceTest}`,
`model/dto/ResultadoSorteioDTO`.

- [ ] Em `sortear(List<Vaga>)`, trocar "ordem de id" por **ordem de aperto**, recalculada a cada passo (guloso
      dinâmico): a cada rodada, entre as vagas que ainda faltam preencher, avaliar os candidatos de cada uma (já sem
      quem foi escolhido nas rodadas anteriores, porque `UmaVagaPorCelebracaoRegra` os impede) e sortear **a de menor
      folga**, onde `folga = elegíveis disponíveis - faltam`. Empate: menos elegíveis primeiro; persistindo, menor id
      (resultado determinístico para a mesma semente). Vaga com `folga < 0` (já se sabe que ficará incompleta) também
      entra, pela mesma ordem (a pessoa rara vai para onde ela ainda completa uma vaga, não para a que já é perdida)
- [ ] O custo é de `N` vagas por celebração avaliando `N` vezes cada (as vagas de uma pastoral por celebração são
      poucas, em geral menos de 10); se medir mal, reaproveitar a avaliação da rodada anterior e recalcular só o que
      mudou. Medir antes de otimizar
- [ ] `SorteioService.escolher` **não muda** (continua escolhendo quem serviu há mais tempo, com o gerador injetado);
      só a **ordem das vagas** muda
- [ ] A ordem em que `convidados` e `incompletas` aparecem em `ResultadoSorteioDTO` pode mudar (hoje, por id). Não é
      contrato; o front não deve depender dela. Se alguém depender, ordenar a resposta por id antes de devolver
- [ ] `sortearVaga` (uma vaga só) não muda

Cuidados com testes existentes:

- [ ] `SorteioIT.poucosElegiveisPreencheOQueDaEDevolveAVagaIncompleta`, `sortearDeNovoNaoDuplica`,
      `duasThreadsSorteandoAMesmaVagaNuncaPassamDaQuantidade`: continuam valendo; o lock por vaga
      (`travar`, ordenado por id) **não** muda, só a ordem do preenchimento depois que todas estão travadas
- [ ] Testes que usam a semente fixa (`SorteioServiceTest`) não são afetados (só a ordem das vagas muda, não o
      sorteio dentro de uma vaga)

Testes novos — em `SorteioIT` e `EscalacaoSorteioOrdemTest` (unitário da escolha de ordem, sem Spring, se der para
isolar a função de ordenação):

- [ ] Cenário do exemplo: com a ordem de id desfavorável, o sorteio preenche **as duas** vagas (a pessoa rara vai
      para Som, Leitura fica com um dos outros candidatos); rodar com várias sementes, nunca deixa Som vazia
- [ ] Com folga igual, o resultado é determinístico (menos elegíveis; depois menor id)
- [ ] Vaga que não tem ninguém elegível não impede as outras: volta em `incompletas` e as demais são preenchidas
- [ ] Dois sorteios simultâneos da mesma celebração continuam sem passar da quantidade (o teste de threads já existe)

### Cartão 5 — Endpoint de membro com status de ativação (lista com filtros e detalhe)

Problema: o front monta a tela Membros (busca, filtros) e o Detalhe do membro. Hoje `GET /api/pastorais/{id}/membros`
devolve a lista inteira, sem filtro, e a sprint 2 só acrescentou o booleano `ativada`. Falta distinguir conta
pendente (nunca ativada), conta protegida (a pessoa usou "Não fui eu" e ainda não escolheu senha nova) e conta
ativa, e falta um detalhe com habilidades e foto.

Ler: `controller/PastoralLeituraController` (`membros`), `service/leitura/LeituraEscalaService` (`membros`,
`exigirPastoralVisivel`), `model/dto/EscalaDTOs.MembroPastoralDTO`,
`repository/UsuarioPastoralRepository` (`membrosDaPastoral`), `repository/AlocacaoRepository` (`ultimosServicos`,
`contarDoUsuarioNaPastoral` do `/api/me`), `model/Usuario` (`estaAtivada`, `ativadoEm`, `senha`),
`service/conta/CadastroMembroService` (reenvio de ativação) e `controller/MembroCadastroController` (sprint 2),
`pastoral/LeituraIT`, `pastoral/UsuarioPastoralIT`.

- [ ] Enum `StatusAtivacao` (`model/enumerated`): `PENDENTE` (sem senha e nunca ativada: o link de ativação está
      esperando), `PROTEGIDA` (sem senha, mas já ativada: a pessoa usou "Proteger minha conta" e falta definir a senha
      nova por e-mail), `ATIVA` (tem senha). Método `StatusAtivacao.de(Usuario)`; sem consulta nova
- [ ] `GET /api/pastorais/{pastoralId}/membros` **continua devolvendo lista** (não vira `Page`: o front atual
      depende disso) e ganha filtros opcionais, todos combináveis: `nome` (parte do nome, sem diferenciar maiúsculas
      nem acentos), `papel` (`PapelPastoral`), `status` (`StatusAtivacao`) e `habilidade` (id de função da
      pastoral). Parâmetro inválido: 400. O filtro roda no banco (uma consulta), não em memória
- [ ] `MembroPastoralDTO` ganha `statusAtivacao`; `ativada` (sprint 2) **continua** para o front que já o usa. O
      que cada papel vê, na mesma regra de hoje do e-mail: **`email` e `statusAtivacao` só para quem coordena** (ou
      PADRE/ADMIN, que valem como coordenador); os demais veem nome, papel e `fotoUrl`, sem contato nem status.
      (Hoje `ativada` vai a todos; se for apertar para a gestão, é uma decisão do `Pendente/decisões`: padrão
      proposto: mantém como está, sem mudar comportamento fora do cartão)
- [ ] `GET /api/pastorais/{pastoralId}/membros/{usuarioId}` → `MembroDetalheDTO`: `usuario {id, nome, fotoUrl}`,
      `papel`, `habilidades [{funcaoId, nome}]` (só as marcadas), `ultimoServico` (data, ou `null`),
      `convitesPendentes` (quantos `PENDENTE` ele tem **nesta pastoral**) e, **só para coordenação**, `email`,
      `statusAtivacao`, `ativadoEm`. **Sem** telefone, senha, hash ou qualquer token. Pessoa sem papel nesta pastoral
      ou de outra paróquia: 404; pastoral fora do alcance: 404 (como `exigirPastoralVisivel`)
- [ ] O botão "reenviar ativação" da tela usa o `POST .../membros/{usuarioId}/reenviar-ativacao` da sprint 2; nada a
      criar aqui
- [ ] Foto: `fotoUrl` vem do cartão 6 da sprint 2 (`/api/fotos/{id}?v=...`); se a sprint 2 ainda não tiver `fotoUrl`
      na `main`, o campo sai `null` e este cartão não bloqueia
- [ ] Atualizar o OpenAPI (`OPENAPI_ENABLED=true`) e avisar o front para rodar `npm run gen:api` (o cliente gerado é
      commitado; precisa de Postgres no ar, ver `frontend/orval.config.ts`)

Cuidados com testes existentes:

- [ ] `LeituraIT` e quem constrói `MembroPastoralDTO` (`LeituraEscalaService.membros`): o construtor ganha o
      campo novo; acertar as chamadas. Conferir que o e-mail continua `null` para quem não coordena
- [ ] `OpenApiIT`: acrescentar o caminho `/api/pastorais/{pastoralId}/membros/{usuarioId}`

Testes novos — `MembrosStatusIT`:

- [ ] Conta cadastrada pela sprint 2 e nunca ativada: `PENDENTE`; depois de ativar: `ATIVA`; depois de "Proteger
      minha conta": `PROTEGIDA` (usar os serviços/rotas reais da sprint 2 para montar o estado)
- [ ] Filtros: por `nome` (com acento e maiúscula diferentes), por `papel`, por `status`, por `habilidade`, e combinados;
      parâmetro inválido: 400; sem filtro, devolve todos (o mesmo de hoje)
- [ ] Membro comum não vê `email` nem `statusAtivacao` (nem na lista, nem no detalhe); coordenador, padre e admin veem
- [ ] Detalhe: habilidades marcadas, último serviço, convites pendentes **só desta pastoral**; sem telefone/senha/token
      na resposta (conferir as chaves do JSON)
- [ ] Pessoa de outra pastoral, de outra paróquia e id inexistente: 404; pastoral fora do alcance: 404
- [ ] Número de consultas não cresce com o número de membros (um teste que conta as consultas, ou que roda com 30
      membros e confere que a lista sai em uma consulta principal mais as de apoio em lote)

## Contrato combinado (proposta do back-end; ver `sprint-0.md`, seção 5)

> O "Mapa de endpoints" do front não estava disponível quando este documento foi escrito: **nomes e formatos abaixo
> são a proposta do back-end** e precisam ser confirmados com o colaborador antes dos cartões 1 e 5.

- [ ] `POST/PUT/DELETE /api/usuarios-funcoes`: passam a aceitar o coordenador da pastoral da função (hoje só
      PADRE/ADMIN); 422 se a pessoa não é da pastoral
- [ ] `GET/PUT /api/pastorais/{id}/membros/{usuarioId}/habilidades` (`PUT` com `{ funcaoIds: [] }`)
- [ ] `GET /api/regras/catalogo`: nova regra `HABILIDADE` (sem parâmetros); `GET /api/pastorais/{id}/config` e
      `PUT /api/pastorais/{id}/config/HABILIDADE` passam a poder ligar e desligar `HABILIDADE`
- [ ] `GET /api/vagas/{id}/candidatos` e `POST /api/alocacoes`: motivo novo "Não tem a habilidade desta vaga" (422
      na escalação manual, forçável pelo coordenador)
- [ ] `POST .../sortear`: sorteia todos os papéis com a habilidade; vagas voltam em `incompletas` quando ninguém tem
      a habilidade
- [ ] `GET /api/pastorais/{id}/membros?nome=&papel=&status=&habilidade=`: lista (mesmo formato, com
      `statusAtivacao` para a coordenação); `GET /api/pastorais/{id}/membros/{usuarioId}`: detalhe
- [ ] Mensagens que o front mostra: sem habilidade, pessoa fora da pastoral, vaga sem elegíveis, muitas tentativas

## Fechamento da sprint

- [ ] Atualizar `Features/features-implementadas.md` e `Features/erros-conhecidos.md` com o que mudou
- [ ] Atualizar `Features/runbook-piloto.md`: **cadastrar as habilidades de cada membro antes do piloto** (sem
      habilidade ninguém é sorteado), e como o coordenador desliga a regra `HABILIDADE` se precisar
- [ ] Resumo por cartão: o que mudou, testes novos, e qualquer coisa que ficou pendente ou decidida diferente do
      planejado (com o motivo)
- [ ] Conferir o "Pronto quando": numa celebração de teste com vagas de funções diferentes, o sorteio só convida quem
      tem a habilidade de cada vaga, e a vaga mais apertada é a primeira a ser preenchida

## Pendente/decisões

Dúvidas reais deste planejamento. Nenhuma foi decidida por palpite: a coluna "padrão proposto" é o que o código faz
**se ninguém decidir diferente**, para não travar o cartão.

| Dúvida | Padrão proposto | Cartão |
|---|---|---|
| "Técnico" também como habilidade na escala (som, telão)? (prazo 23/11; já decidido como "só como cargo" em `features-futuras.md`) | Só como cargo: não nasce função "Técnico" no catálogo. Quem faz som/telão é uma função comum com a habilidade marcada para quem sabe | 2, 3 |
| Regra `HABILIDADE` ligada por padrão: uma pastoral que ainda não cadastrou habilidades **não sorteia ninguém** até cadastrar | Ligada por padrão (igual às outras regras). Não há dado em produção ainda (piloto só em 15/02) e o `runbook-piloto.md` ganha o passo "cadastrar habilidades". Alternativa: nascer **desligada** e o coordenador ligar quando terminar de cadastrar (mais segura para quem já usa o sistema, mas o "pronto quando" deixa de valer sozinho) | 2 |
| Os ITs antigos rodam com a regra `HABILIDADE` **desligada** (`servio.escalacao.habilidade-ligada-por-padrao=false` só no `application-test.properties`), então **não exercitam a interação com a regra ligada**; só `HabilidadeRegraIT`, `HabilidadeRegraTest` e (a partir do cartão 3) o `SorteioIT` a exercitam | Aceito nesta sprint: evita semear habilidade em ~10 ITs sem Docker local e com CI instável. **Tarefa futura:** quando houver Docker local ou CI estável, ligar a regra em todos os ITs (remover a linha do `application-test.properties`) e semear habilidade em cada fábrica de usuário/cenário | 2 |
| Quem marca habilidade: só o coordenador da pastoral, ou também o vice? E a própria pessoa ("eu sei fazer som")? | Só coordenador (e padre/admin). Vice e a própria pessoa ficam para depois: a própria pessoa pedir exige um fluxo de aprovação | 1 |
| Sem a restrição "só MEMBRO", coordenação e cargos passam a ser sorteáveis desde que tenham a habilidade. Alguma pastoral quer que a coordenação **nunca** seja sorteada, mesmo com habilidade? | Não: quem tem a habilidade é sorteável. Se for necessário, é um parâmetro da pastoral (`papeisSorteaveis` em `pastoral_config`), fora desta sprint | 3 |
| Guloso dinâmico (recalcular a cada vaga) x ordem calculada uma vez | Dinâmico: a ordem certa depende de quem já foi escolhido, e o custo é baixo (poucas vagas por celebração). Não é um solucionador ótimo; em casos raros um arranjo melhor existe e o guloso não acha (a pastoral completa pelo manual) | 4 |
| `ativada` (boolean da sprint 2) e `email` aparecem para todos os papéis hoje? | Mantém como está nesta sprint. Revisão de privacidade (esconder `ativada`/`email` de quem não coordena) fica registrada, sem mudar comportamento fora do cartão | 5 |
| Detalhe do membro mostra telefone para a coordenação? | Não nesta sprint: telefone de outra pessoa só aparece quando houver uma tela e uma regra de privacidade definidas. Fora do detalhe: nada de telefone em listas | 5 |
| Conta pendente (cadastrada e nunca ativada) não expira (já registrado na sprint 2) | O `StatusAtivacao.PENDENTE` deixa a coordenação **ver** quem está parado; o job de limpeza continua em aberto | 5 |
| Nomes de rotas e de campos (sem o "Mapa de endpoints") | Proposta no "Contrato combinado"; confirmar com o colaborador antes de começar os cartões 1 e 5 | 1 a 5 |
| `UsuarioFuncao.desativar` hoje é exclusão lógica e o histórico de quem tinha a habilidade se perde se alguém desmarcar e remarcar | Aceito: o `AuditLog` `HABILIDADE_ALTERADA` registra cada mudança | 1 |

## Fora desta sprint

- [ ] Telas Membros (busca, filtros), Detalhe do membro e Adicionar pessoa: trabalho do colaborador
- [ ] Habilidade com nível (iniciante/experiente), peso no sorteio por habilidade, sorteio ótimo (em vez de guloso),
      parâmetro `papeisSorteaveis`, a própria pessoa pedir uma habilidade

Ver também: `sprint-2.md`, `sprint-1.md`, `sprint-0.md`, `features-futuras.md`.
