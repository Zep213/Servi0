# Servio — Sprint 0: preparação

**Período:** 13/10 a 25/10/2026 · **Back-end:** Zep · **Front-end:** colaborador

**Objetivo:** os dois trabalham no mesmo código, do mesmo jeito, e a sprint 1 começa sem nada travado.

**Pronto quando:** os dois sobem o sistema completo na própria máquina, o CI está verde, o contrato do `/api/me` está combinado e o quadro da sprint 1 está montado.

---

## 1. Acesso e combinados

- [x] Dar acesso de escrita ao colaborador em `Zep213/Servi0` (Settings → Collaborators)
- [ ] Proteger a branch `main`: merge só por PR, com 1 aprovação e o CI verde
- [x] Decidir se o repositório fica privado já — adiantado para 14/10 (era previsto 15/02); repositório mudado para privado. Dependabot continua ativo; secret scanning/push protection nativos do GitHub saem do ar (exigiam ser público), mas o gitleaks do `ci.yml` continua rodando.
- [ ] Combinar o fluxo de trabalho:
  - um branch por cartão (`feat/fila-email`, `fix/lembrete-token`)
  - PR pequeno; o outro revisa em até 48 horas
  - o back-end publica o OpenAPI e o front gera o cliente com `npm run gen:api`
  - contrato da rota combinado **antes** de codar; o front começa com dados simulados (MSW)
- [ ] Montar o quadro (GitHub Projects ou Trello) com as colunas A fazer, Fazendo, Em revisão e Pronto
- [ ] Marcar os dois rituais de 30 minutos: começo e fim de cada sprint

## 2. Ambiente local (os dois)

**Ferramentas**

- [ ] Java 25 (Temurin) e Maven pelo wrapper (`./mvnw`)
- [ ] Node 22 (`frontend/.nvmrc`)
- [ ] Docker com Compose
- [ ] Um cliente de banco (DataGrip ou DBeaver) apontando para `localhost:5432`, banco `servio`

**Subir o sistema**

- [ ] `cp .env.example .env` e trocar as senhas de exemplo
- [ ] Banco e e-mail: `docker compose up -d db mailpit`
- [ ] Back-end: `./mvnw spring-boot:run` (lê o `.env` sozinho); o `BootstrapAdmin` cria a paróquia e o ADMIN na primeira subida
- [ ] Front-end: `cd frontend && npm ci && npm run dev` (porta 5173, com proxy para a 8080)
- [ ] Conferir no `.env`: `SERVIO_FRONT_URL=http://localhost:5173` e `COOKIE_SECURE=false` (sem HTTPS local)
- [ ] Entrar com o ADMIN, criar a pastoral Pascom, um coordenador e dois membros
- [ ] Sortear uma missa e abrir o convite pelo Mailpit (`http://localhost:8025`)
- [ ] Rodar os testes: `./mvnw verify` (back-end, com Testcontainers) e `npm test` (front)

> Se a porta 5432 já estiver ocupada por outro Postgres local, mude `DB_PORT` no `.env`.

## 3. Repositório em dia

- [ ] CI verde no `main` (`ci.yml`: gitleaks, testes, build da imagem, Trivy e job `frontend`)
- [x] `Features/erros-conhecidos.md`: mover o item 13 (coordenador criar ADMIN) para Resolvidos; o `UsuarioService.exigirPodeDefinirPerfil` já impede
- [x] `Features/features-implementadas.md`: tirar o item 13 de "Em desenvolvimento"
- [x] `Features/features-futuras.md`: trocar pela ordem do cronograma (sprints 1 a 7) — feito para a sprint 1; sprints 2 a 7 pendentes do documento completo do cronograma
- [x] Copiar para `Features/` os documentos de planejamento: `sprint-0.md` — `sprint-1.md` e os links (protótipo, mapa de endpoints, cronograma) ainda não foram recebidos
- [x] Ajustes pequenos encontrados na revisão:
  - [x] Adicionar `spring-boot-starter-actuator` ao `pom.xml`: a rota `/actuator/health` já está liberada no `SecurityConfig`, mas a dependência não existia
  - [x] Remover a linha repetida de `/actuator/health` no `SecurityConfig`

## 4. Estudo das telas

**Os dois**

- [ ] Percorrer o protótipo inteiro: membro, coordenação, computador, modo escuro e e-mails
- [ ] Ler o mapa de endpoints e marcar as dúvidas como comentário no próprio documento

**Front-end (colaborador)**

- [ ] Listar o que muda nos componentes atuais (`Casca`, `Card`, `Botao`, `Tabela`, `Modal`) e o que é novo (barra lateral, sininho, avatar, seletor de aparência, calendário do mês)
- [ ] Separar os tokens de cor de `tokens.css` em claro e escuro, sem mudar ainda nenhuma tela
- [ ] Rascunhar os mocks do MSW para o novo `GET /api/me`

**Back-end (Zep)**

- [ ] Ler o fluxo atual de e-mail (`NotificacaoListener`, `EnvioEscalacao`, `LembretesJob`) para planejar a fila de e-mails
- [ ] Ler o fluxo de login (`UsuarioDetailsService`, `LoginRateLimitFilter`, `LoginHandlers`, `SessaoService`) para planejar o login por telefone

## 5. Contrato da sprint 1

Combinar antes de 26/10 e registrar no mapa de endpoints:

- [ ] `GET /api/me`: campos novos `telefone`, `aparencia`, `pedirTelefone` e `pastorais[].pendencias`
- [ ] `PUT /api/me`: `{ telefone?, aparencia?, pularTelefone? }`; 409 para telefone já usado
- [ ] `POST /api/auth/login`: o campo do formulário muda de `email` para `login`
- [ ] Mensagens de erro que o front vai mostrar (telefone inválido, telefone em uso, login inválido, muitas tentativas)

## 6. Decisões e providências com prazo

| O quê | Até | Por quê |
| --- | --- | --- |
| Domínio do Servio e o e-mail remetente | 08/11 | SPF, DKIM e DMARC precisam estar no domínio antes da ativação por e-mail (sprint 2); sem isso, os e-mails caem no spam |
| Textos dos e-mails (rascunhos no protótipo) | 09/11 | Entram na sprint 2 |
| Arquivos do escudo da Pascom e do logo do Servio | 15/11 | Hoje são espaços reservados |
| Onde hospedar (servidor e custo mensal) | 01/02 | Deploy de teste na sprint 7 |

---

## Fora desta sprint

Nenhuma funcionalidade nova entra na sprint 0. A primeira entrega de código é a sprint 1 (26/10 a 08/11): fila de e-mails, lembrete sem invalidar o link, login por e-mail ou telefone, sessão por cargo, `/api/me` e a casca nova no front. Os detalhes estão em `sprint-1.md`.
