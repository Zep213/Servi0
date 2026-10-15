# Servio — Sprint 7

**Período:** 01/02 a 14/02/2027 · **Back-end:** Zep · **Front-end:** colaborador

Estabilização. **Pronto quando:** uma pessoa de fora usa o sistema no celular sem ajuda, num servidor de teste com
HTTPS, com backup já restaurado uma vez e o ZAP rodado sem achado alto em aberto.

Stack: Spring Boot 4.1.1, Java 25, Maven wrapper, JPA/Hibernate, Lombok, MapStruct, Flyway, PostgreSQL 17,
Spring Session JDBC, Spring Security com `formLogin`, Thymeleaf para e-mails, Testcontainers nos ITs (`*IT`), Docker
Compose (`db`, `mailpit`, `app`, `web` com nginx).

Detalhado a partir de `features-futuras.md` (seção "Sprint 7", "Decisões pendentes" e "Segurança e acesso"),
`checklist-seguranca.md` e `runbook-piloto.md`; ver também `sprint-0.md`. Escopo desta sprint é **só back-end e
infraestrutura**: ajustes de acessibilidade, teste completo no celular dos coordenadores e os testes Playwright dos
fluxos novos são do colaborador (front). Esta sprint não cria funcionalidade de escala: o que muda no código é pequeno
(health check, uma coluna de aceite dos termos); o resto é compose, scripts, CI e documentação.

**Piloto: 15/02 a 28/02.** O piloto começa logo depois: o que não estiver pronto aqui entra no piloto como risco
conhecido, escrito em `erros-conhecidos.md`. **Carnaval (segunda 08/02 e terça 09/02)** cai dentro da sprint: conferir no calendário da
paróquia e contar a semana como mais curta (ver Pendente/decisões).

## Pré-requisitos (o que já precisa estar na `main`)

- [ ] Sprints 1 a 6 mergeadas (a sprint 6 pode ter saído do MVP, ver `features-futuras.md`: neste caso as sprints 1 a
      5). O deploy de teste roda a `main` inteira, com as migrations todas
- [ ] **Decisão de hospedagem tomada** (servidor, domínio, custo mensal): prazo **01/02**, sem padrão definido. Sem ela
      os cartões 2 e 3 só chegam até o que independe do servidor (scripts, compose, documentação). Ver
      Pendente/decisões
- [ ] **Decisão sobre o repositório (público ou privado)**: prazo 15/02, padrão "privado antes do piloto". Muda como o
      código chega ao servidor e a cota do CI. Ver Pendente/decisões
- [ ] Domínio e remetente de e-mail (SPF, DKIM, DMARC) configurados (`sprint-0.md`, seção 6): sem eles o e-mail de
      ativação e de convite cai no spam, e o "pronto quando" não se cumpre

## Regras de trabalho

- [ ] Antes de cada cartão: ler os arquivos citados nele e mostrar o plano antes de começar
- [ ] Um branch por cartão, a partir do `main`: `feat/health-check`, `feat/backup-restore`, `feat/deploy-teste`,
      `feat/config-producao`, `feat/zap`, `feat/termos`, `docs/runbook-backend`. O cartão 3 depende do 1 e do 2:
      empilhar no branch do cartão de que depende e dizer isso no PR
- [ ] Commits pequenos, mensagem em português
- [ ] Migrations só no cartão 6, a partir do próximo número livre depois da sprint 6 (o texto usa `V24`; conferir
      `ls src/main/resources/db/migration` antes de nomear). Nunca editar migration que já existe
- [ ] `./mvnw verify` ao fim de cada cartão que mexe em Java (precisa do Docker ligado); não passar para o próximo com
      teste vermelho. Cartões de script e documentação: rodar o script de verdade e colar a saída no PR
- [ ] **Nada de segredo no repositório**: senha, token, chave SSH, credencial de backup, `.env.prod` real. Só modelos
      (`.env.prod.example`) com valores vazios. O gitleaks do CI continua barrando
- [ ] **ZAP e qualquer teste ativo só no servidor de teste, com dados fictícios.** Nunca contra o piloto com dados reais
- [ ] Todo script novo tem `set -euo pipefail`, não imprime senha nem conteúdo de `.env`, e se recusa a rodar sem as
      variáveis obrigatórias
- [ ] Não mudar comportamento da aplicação fora do que está descrito em cada cartão; o que parecer errado no caminho
      vai pra "Pendente/decisões" no fechamento
- [ ] Comentários, mensagens e documentação em português, no mesmo estilo do código atual

## Padrão que os cartões seguem (já existe no repositório)

- **Compose** (`docker-compose.yml`): `db` (postgres:17, porta só em `127.0.0.1`, volume `servio-db-data`,
  `healthcheck` com `pg_isready`), `mailpit` (só dev, mas **sobe também em produção**: o runbook manda "ignorar"),
  `app` (build do `Dockerfile`, **sem `healthcheck`**, `depends_on: db: service_healthy`) e `web` (nginx do front,
  porta `${WEB_PORT:-80}`, `depends_on: app` só como "started"). Variáveis por `.env` (dev) e `.env.prod` (copiado de
  `.env.prod.example`, passado com `--env-file`). Sem limite de memória nem rotação de logs
- **Imagem do app** (`Dockerfile`): build em `maven:3.9-eclipse-temurin-25`, runtime `eclipse-temurin:25-jre` com usuário
  sem privilégio (`servio`). Imagem do front: `nginxinc/nginx-unprivileged`. Flyway roda sozinho ao subir o app (só para
  frente: **não existe rollback de migration**)
- **Actuator**: `spring-boot-starter-actuator` já está no `pom.xml` e `GET /actuator/health` já é `permitAll` no
  `SecurityConfig`; o nginx só repassa `/api/`, então `/actuator/health` **não** é alcançável pelo `web` (só direto no
  `app:8080`). `application.properties` não tem nenhuma propriedade `management.*`
- **Proxy e IP**: `server.forward-headers-strategy=native` e o nginx repassa `X-Forwarded-For/Proto/Host/Port`; o rate limit
  do login e os limites por IP das páginas públicas dependem do IP real
- **Segurança já no código**: cookie `SERVIO_SESSION` `HttpOnly`/`Secure`/`SameSite=lax`, CSRF `spa()`, CSP/`frame-ancestors`
  nas respostas da API (`SecurityConfig`), `server.error.include-stacktrace/message=never`, OpenAPI desligado por padrão
  (`OPENAPI_ENABLED`), conta de demonstração somente leitura
- **CI** (`.github/workflows/ci.yml`): gitleaks, build e testes (`./mvnw verify`), build da imagem, scan de imagem
  (trivy, `CRITICAL,HIGH`, `ignore-unfixed`, com `.trivyignore` de 3 CVEs do pebble da imagem base), job do front (lint,
  formatação, tipos, testes, build) e `e2e.yml` (Playwright). Dependabot ativo (`dependabot.yml`)
- **Runbook do piloto** (`runbook-piloto.md`): Opção A (a própria máquina) e Opção B (VM Oracle Always Free), túnel
  **rápido** do `cloudflared` (a URL **muda** a cada reinício, e os links dos e-mails já enviados morrem), conta de
  demonstração. O runbook diz que "o repositório é público" para clonar na VM
- **Checklist** (`checklist-seguranca.md`): HTTPS, `X-Forwarded-For`, senhas do `.env`, porta do banco fechada, backup
  **restaurado ao menos uma vez**, CI verde, logs sem segredos, ZAP no staging. Hoje nenhum item está marcado

## Escopo (7 cartões, nesta ordem)

### Cartão 1 — Health check e prontidão

Problema: a aplicação não tem `healthcheck` no compose. O `web` sobe antes de o `app` estar pronto, o Docker não
reinicia um `app` que travou sem cair, e ninguém de fora consegue ver se o sistema está de pé. O `/actuator/health`
existe, mas sem configuração e fora do alcance do nginx.

Ler: `pom.xml` (actuator), `config/SecurityConfig` (`/actuator/health`), `application.properties` (não há `management.*`),
`docker-compose.yml` (`db.healthcheck` como modelo), `Dockerfile` (imagem de runtime), `frontend/nginx.conf`,
`Features/checklist-seguranca.md`, `features-futuras.md` ("healthcheck do container `app` no compose").

- [ ] Propriedades: `management.endpoints.web.exposure.include=health`, `management.endpoint.health.show-details=never`,
      `management.endpoint.health.probes.enabled=true` (grupos `liveness` e `readiness`), e
      `management.health.mail.enabled=false`: o indicador de e-mail do Actuator testa o SMTP e deixaria o sistema
      "fora do ar" só porque o Gmail ficou lento (conferir no código se ele vem ligado; ver Pendente/decisões). O
      health do banco continua valendo (sem banco, o app não serve). Nenhum outro endpoint do Actuator fica exposto
- [ ] `SecurityConfig`: `GET /actuator/health` e `GET /actuator/health/**` (apenas esses, `permitAll`); todo o resto de
      `/actuator/**` continua caindo no `denyAll`
- [ ] `docker-compose.yml`:
  - [ ] `app.healthcheck` chamando `http://localhost:8080/actuator/health/readiness` de dentro do container
        (`interval` 15 s, `timeout` 5 s, `retries` 5, `start_period` 60 s: o Flyway e o Hibernate demoram a subir). A
        imagem de runtime é `eclipse-temurin:25-jre`: conferir qual cliente HTTP ela tem (`curl`/`wget`); se não tiver,
        **não** instalar pacote só para isso sem ver o scan do trivy: usar um comando `java` de uma linha ou um
        endpoint de `HEAD` equivalente (ver Pendente/decisões)
  - [ ] `web.depends_on.app.condition: service_healthy`; `web.healthcheck` simples (`wget -q --spider http://127.0.0.1:8080/`
        da imagem `nginx-unprivileged`)
  - [ ] `restart: unless-stopped` já existe: confirmar que, com o `healthcheck`, um `app` doente é percebido
        (`docker compose ps` mostra `unhealthy`; reiniciar por `unhealthy` exige `autoheal` ou política de deploy:
        ver Pendente/decisões)
- [ ] Endpoint externo para monitor de disponibilidade: `GET /healthz` no nginx do `web`, que repassa **só** para
      `app:8080/actuator/health` (sem detalhes) e responde 200/503, para um monitor gratuito de uptime olhar a URL
      pública. `/actuator/**` continua inalcançável pelo nginx
- [ ] Documentar no `checklist-seguranca.md` (nova linha) que o health não expõe detalhe nenhum e que o `app` não
      publica porta no host em produção

Cuidados com testes existentes:

- [ ] `VazamentoIT`, `AutorizacaoIT`: `/actuator/env`, `/actuator/beans`, `/actuator/info` etc. continuam respondendo o
      mesmo de antes (negado), e `GET /actuator/health` continua 200 sem login
- [ ] `application-test.properties` não precisa de mudança

Testes novos — `HealthCheckIT`:

- [ ] `GET /actuator/health` sem login: 200, corpo `{"status":"UP"}` **sem** `components` nem detalhes
- [ ] `GET /actuator/health/liveness` e `/readiness`: 200 `UP`
- [ ] `GET /actuator/env`, `/actuator/beans`, `/actuator/metrics`, `/actuator/info`, `/actuator/heapdump`: nenhum devolve
      200 (nem logado como ADMIN)
- [ ] Com o banco parado (`DataSource` apontando para uma porta fechada, num contexto próprio) o `readiness` não devolve
      200 (vira 503 `DOWN`); com o SMTP inalcançável o health continua `UP` (indicador de e-mail desligado)
- [ ] `POST /actuator/health`: 405 ou 403, nunca 200

Verificação manual (colar no PR): `docker compose up -d --build`, esperar, `docker compose ps` mostra `healthy` nos três;
`docker compose stop db` e conferir que o `app` fica `unhealthy` e volta sozinho depois de `docker compose start db`.

### Cartão 2 — Backup e restauração testada

Problema: não existe backup. O único dado da paróquia está no volume `servio-db-data`, e o item "backup testado" do
checklist exige já ter **restaurado** um. O Flyway só anda para frente, então o backup também é o "rollback" de um
deploy ruim. Decisão: backup lógico diário com `pg_dump` mais um backup antes de cada deploy, cópia **fora do
servidor**, e um script que **prova** a restauração.

Ler: `docker-compose.yml` (`db`, volume), `.env.prod.example` (`POSTGRES_*`), `runbook-piloto.md`,
`checklist-seguranca.md` (item de backup), `src/main/resources/db/migration/V3__spring_session.sql` (sessões ficam no
banco), `.github/workflows/ci.yml` (padrão de job).

- [ ] Pasta `scripts/` (nova) com, cada um com `--ajuda` e `set -euo pipefail`:
  - [ ] `scripts/backup.sh`: `docker compose exec -T db pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"` para
        `backups/servio-AAAAMMDD-HHMMSS.dump` (formato custom, comprimido), permissão `600`, **sem** a senha na linha de
        comando nem no log; confere que o arquivo não está vazio e roda `pg_restore --list` nele (arquivo corrompido
        falha o script); apaga os mais velhos que a retenção (`BACKUP_RETENCAO_DIAS`, padrão 14, mais os 4 últimos
        semanais); opcionalmente copia para fora do servidor (`BACKUP_DESTINO` = comando `rclone`/`scp`, ver
        Pendente/decisões). Sai com código diferente de zero em qualquer falha
  - [ ] `scripts/restaurar.sh <arquivo>`: restaura num banco **novo** (`servio_restore_AAAAMMDD`), nunca por cima do
        banco em uso; mostra a contagem de linhas das tabelas principais (`usuario`, `paroquia`, `celebracao`,
        `alocacao`, `flyway_schema_history`) para comparar; para substituir de verdade o banco de produção há um
        passo manual documentado no runbook (parar o `app`, renomear bancos), não automático
  - [ ] `scripts/testar-restauracao.sh`: faz o backup, restaura num banco descartável, sobe o `app` contra ele (perfil de
        teste, porta alternativa) e confere `GET /actuator/health` = `UP`, que o Flyway não pediu migration nova e que a
        contagem de `usuario` bate com a do original; apaga o banco descartável no fim. Imprime `RESTAURAÇÃO OK` ou
        falha
- [ ] Agendamento: um `cron` no servidor (`0 3 * * *`, fuso da paróquia) chamando `scripts/backup.sh`, documentado no
      runbook com o comando exato (`crontab -e`) e com como conferir que rodou (`ls -lh backups/`). O deploy (cartão 3)
      chama o backup **antes** de subir a versão nova
- [ ] `.gitignore`: `backups/` e `*.dump` (nunca no repositório); gitleaks não pode reclamar
- [ ] O backup contém dados pessoais (nome, e-mail, telefone, foto, escalas) e `usuario.senha` (hash BCrypt): arquivo
      `600`, nunca em pasta pública nem em e-mail; a cópia remota tem de ser **criptografada** (ver Pendente/decisões)
- [ ] Job de CI **opcional e manual** (`workflow_dispatch`, não em `push`): `scripts/testar-restauracao.sh` contra um
      Postgres do próprio runner. A fila do CI já é longa (ver Pendente/decisões), então o padrão é rodar o script à mão
      e colar a saída
- [ ] Registrar em `checklist-seguranca.md` a data da **primeira restauração real** e quem a fez

Testes e verificação:

- [ ] `scripts/testar-restauracao.sh` rodando de verdade (saída colada no PR): `RESTAURAÇÃO OK`
- [ ] Backup de um arquivo vazio/truncado: `backup.sh` falha e **não** conta como backup; retenção apaga só os antigos
- [ ] Rodar duas vezes no mesmo segundo não sobrescreve (nome com data e hora, ou sufixo)
- [ ] `grep -r` no log do script e no `docker compose logs` não acha a senha do banco

### Cartão 3 — Compose de produção e deploy no servidor de teste

Problema: o `docker-compose.yml` é o de desenvolvimento (Mailpit junto, porta do banco aberta em `127.0.0.1`, porta 80
sem HTTPS, sem limites). O piloto roda no túnel rápido, cuja URL muda e quebra os links dos e-mails. Decisão: um
servidor de teste **fixo**, com HTTPS e domínio, subido por um comando e com volta atrás documentada. Empilhar nos
cartões 1 e 2. Depende da decisão de hospedagem.

Ler: `docker-compose.yml`, `Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.env.prod.example`,
`.env.example`, `runbook-piloto.md` (Opção B e túnel), `.github/workflows/ci.yml`, `src/main/resources/application.properties`
(`SERVIO_FRONT_URL`, `COOKIE_SECURE`, `server.forward-headers-strategy`).

- [ ] `docker-compose.prod.yml` (usado **junto** do base: `docker compose -f docker-compose.yml -f docker-compose.prod.yml
      --env-file .env.prod up -d`): remove o `mailpit` (`profiles: ["dev"]` no serviço base, para só subir com
      `--profile dev`; o `depends_on: mailpit` do `app` precisa sair ou virar opcional, senão o `app` não sobe sem ele), remove a publicação da porta do `db`, define `mem_limit`/`cpus` razoáveis para uma VM pequena,
      `logging` com `json-file` e `max-size`/`max-file` (rotação), `restart: unless-stopped` e o `healthcheck` do
      cartão 1. Nenhuma porta do `app` publicada: só o proxy HTTPS fala com o `web`
- [ ] HTTPS: um proxy na frente do `web` terminando TLS, com redirecionamento HTTP → HTTPS e `Strict-Transport-Security`
      (a API já recebe HSTS do Spring quando vê `X-Forwarded-Proto: https`; o nginx do front precisa do próprio para
      os estáticos). Opções e padrão em Pendente/decisões (túnel nomeado da Cloudflare ou Caddy com Let's Encrypt). O
      proxy repassa `X-Forwarded-For/Proto/Host`, e o `.env.prod` fecha `SERVIO_FRONT_URL` com a URL **fixa** e
      `COOKIE_SECURE=true`
- [ ] `.env.prod.example`: acrescentar as variáveis novas (`BACKUP_*`, domínio, `SERVIO_FRONT_URL`), todas **vazias**, e
      um aviso de que `SERVIO_DEMO_*` fica em branco em produção real (só no servidor de teste)
- [ ] `scripts/deploy.sh` (no servidor): confere `.env.prod`, roda `scripts/backup.sh` (**aborta o deploy se o backup
      falhar**), `git pull` (ou baixa a imagem, conforme a decisão do repositório), `docker compose ... up -d --build`,
      espera o `healthy`, roda o teste fumaça (`scripts/fumaca.sh`: `GET /healthz` 200, login do admin de teste, `GET
      /api/me` 200, `GET /api/auth/csrf` 200) e imprime a versão (hash do commit) implantada. Se o fumaça falha,
      imprime o comando de volta atrás e **não** apaga nada
- [ ] Volta atrás documentada e **ensaiada uma vez**: imagem anterior (`git checkout <hash>` + build, ou tag da imagem) e,
      se a versão nova rodou uma migration, restaurar o backup feito antes do deploy (o Flyway não desfaz migration)
- [ ] Carga de dados de teste: `scripts/dados-teste.sh` cria, via API/variáveis de bootstrap, uma paróquia fictícia, 2 a 3
      pastorais, uns 12 membros e o horário fixo de missa, para a pessoa de fora usar sem tocar em dado real (usa as
      contas de demonstração somente leitura quando o objetivo for só olhar)
- [ ] Servidor: SSH só com chave, firewall liberando só 22/80/443, atualizações de segurança automáticas
      (`unattended-upgrades`), usuário sem root rodando o Docker. Documentado como lista no runbook (cartão 7), sem script
      que mexa no sistema operacional
- [ ] (opcional, só se a decisão do repositório permitir) job manual do CI que constrói e publica a imagem num registro;
      o servidor só baixa. Se o repositório continuar público, o `git clone` do runbook basta

Cuidados com testes existentes:

- [ ] `docker compose config` (sem `-f` extra) continua válido para quem usa o ambiente de dev; o dev **continua** subindo o
      Mailpit (`docker compose --profile dev up`) — conferir e atualizar o `README`/runbook, porque `mailpit` deixa de
      subir por padrão
- [ ] CI: o passo "Build da imagem" e o trivy não mudam

Verificação (colar no PR):

- [ ] `docker compose -f docker-compose.yml -f docker-compose.prod.yml --env-file .env.prod config` sem erro e **sem**
      `mailpit` nem a porta do `db`
- [ ] No servidor de teste: `https://<domínio>/` abre (HTTP redireciona para HTTPS), `GET /healthz` 200, o cookie de
      sessão sai `Secure`, o e-mail de ativação chega numa caixa real com link para o domínio fixo
- [ ] `docker compose ps`: `db`, `app`, `web` `healthy`; reiniciar a VM (`reboot`) e conferir que tudo volta sozinho e que
      a URL **não mudou**
- [ ] Deploy ensaiado duas vezes: o normal e um propositalmente quebrado (build que falha o fumaça), com a volta atrás
      funcionando

### Cartão 4 — Configuração de produção revisada e checklist de segurança fechado

Problema: o checklist tem oito itens e nenhum marcado, e há decisões de configuração espalhadas (cookie, OpenAPI, erro,
CSP) que só valem se alguém conferir o conjunto no ambiente real. Cartão de **verificação com testes automáticos onde
der** e evidência no PR onde não der.

Ler: `Features/checklist-seguranca.md`, `src/main/resources/application.properties`,
`config/SecurityConfig` (cabeçalhos, matchers públicos, `denyAll`), `frontend/nginx.conf`, `.trivyignore`,
`.github/workflows/{ci.yml, e2e.yml}`, `.github/dependabot.yml`, `service/conta/LimiteContaPublica` e
`service/escalacao/LimiteConfirmacoes` (limites por IP), `security/TentativasLogin`, testes `seguranca/*`.

- [ ] Teste de configuração `ConfigProducaoTest` (unitário, **sem Spring e sem Docker**, lê `application.properties` como
      está no repositório): `server.servlet.session.cookie.secure` padrão **true**, `http-only` true, `same-site` lax;
      `springdoc.*.enabled` padrão **false**; `server.error.include-stacktrace` e `include-message` `never`;
      `server.forward-headers-strategy=native`; `management.endpoints.web.exposure.include` só `health`;
      `servio.jobs.enabled` padrão true; `spring.jackson.deserialization.fail-on-unknown-properties` true. Se alguém
      afrouxar uma dessas, o teste vermelho aponta qual
- [ ] Teste `CabecalhosIT`: as respostas da API trazem `Content-Security-Policy`, `X-Content-Type-Options: nosniff`,
      `Referrer-Policy` e `X-Frame-Options`/`frame-ancestors`; com `X-Forwarded-Proto: https` trazem também
      `Strict-Transport-Security`; resposta de erro (404, 500 forçado) não traz stack trace nem nome de classe
- [ ] Teste `LogsSemSegredosIT` (`OutputCaptureExtension`, no padrão do `AtivacaoContaIT`): rodar login com sucesso e com
      falha, convite, ativação e esqueci a senha **com valores sentinela** (uma senha e um token conhecidos) e conferir
      que a saída dos logs não contém a senha, o token, o hash do token nem o hash BCrypt de nenhuma conta. Falha no
      sistema = achado a corrigir, não teste a afrouxar
- [ ] Revisar o **nginx do front**: HSTS (quando atrás de HTTPS), `server_tokens off`, tamanho máximo de corpo
      (`client_max_body_size`, alinhado ao limite de 3 MB do upload de foto) e que `/actuator/` e `/v3/api-docs` **não**
      são repassados
- [ ] Revisar `.trivyignore`: os 3 CVEs do `pebble` (imagem base) continuam sem correção na imagem atual? Reavaliar com o
      scan do CI; o que tiver correção sai da lista. Revisar também os PRs abertos do Dependabot (versões do front, das
      imagens base) e **decidir** cada um (mergear, adiar com motivo ou fechar) antes do código congelar
- [ ] Varredura de segredos no **histórico inteiro** (`gitleaks detect` local, não só o commit novo): nenhum segredo real
      em nenhum commit antigo. Se achar, rotacionar o segredo (não basta apagar do histórico). Isto é pré-condição para
      qualquer mudança público → privado do repositório
- [ ] Conferir no servidor de teste, e registrar em `checklist-seguranca.md` com data e quem conferiu, cada item do
      checklist: HTTPS, `X-Forwarded-For` chegando ao app (o rate limit do login enxerga o IP do cliente e não o do
      proxy: tentar 6 logins errados de um celular e ver 429 só para ele), senhas fortes e únicas no `.env.prod`, porta do
      banco fechada (`nmap`/`ss -tlnp` do lado de fora), backup restaurado (cartão 2), CI verde, logs sem segredos, ZAP
      (cartão 5)
- [ ] Contas e segredos: `SERVIO_DEMO_*` em branco no ambiente do piloto real; senha do admin de bootstrap trocada depois do
      primeiro acesso (o bootstrap só cria a conta se não houver nenhum usuário); e-mail remetente com SPF/DKIM/DMARC
      conferidos (teste de entrega para Gmail e Outlook, com o cabeçalho "mensagem original" colado no PR)

Testes: os três acima; o resto é verificação com evidência no PR.

### Cartão 5 — ZAP (OWASP ZAP) contra o servidor de teste

Problema: o checklist pede ZAP rodado ao menos uma vez antes do go-live. Nunca foi feito. Decisão: um **baseline
passivo** (rápido, sem risco) e um **scan autenticado da API** contra o servidor de teste com dados fictícios, tudo
disparado **à mão** (não em `push`), com os achados triados e corrigidos em commits pequenos. Empilhar no cartão 3 (precisa
do servidor de teste).

Ler: `Features/checklist-seguranca.md` (item ZAP), `config/SecurityConfig` (CSRF `spa()`, `formLogin` com parâmetros
`login` e `senha`, `GET /api/auth/csrf`), `security/{LoginRateLimitFilter, TentativasLogin}` (429 depois de 5 falhas por
IP/conta), `application.properties` (`OPENAPI_ENABLED`, `/v3/api-docs`), `.github/workflows/e2e.yml` (padrão de job manual),
`controller/AuthController`.

- [ ] Pasta `zap/` (nova): `zap/contexto.yaml` (plano de automação do ZAP: contexto com a URL do servidor de teste,
      autenticação por formulário `POST /api/auth/login` com `login`/`senha`, cabeçalho `X-XSRF-TOKEN` lido do cookie
      `XSRF-TOKEN`, indicador de sessão `SERVIO_SESSION`), `zap/README.md` (como rodar) e **nenhuma credencial**: a
      conta de teste entra por variável de ambiente
- [ ] Alvos e exclusões: o scan usa o OpenAPI (`/v3/api-docs`) para descobrir as rotas, então o `OPENAPI_ENABLED=true` fica
      ligado **só no servidor de teste, só durante o scan**, e é desligado depois (e conferido no checklist);
      **exclui** `POST /api/auth/login` do fuzz (o rate limit de força bruta vai devolver 429 e cegar o resto do scan; o
      login é testado à mão), `POST /api/auth/logout` e as rotas de conta pública (`/api/contas/**`,
      `/api/confirmacoes/**`) ficam num segundo passe baseline, porque os limites por IP derrubam o scan
- [ ] `.github/workflows/zap.yml` com **apenas** `workflow_dispatch` (entrada: URL do servidor de teste): roda
      `zaproxy/action-baseline` e depois o plano autenticado, guarda o relatório HTML/JSON como artefato e **não** quebra
      o build de pull request (nunca roda em `push`/`pull_request`; a fila do CI já é longa)
- [ ] Triagem: relatório → tabela em `Features/zap-relatorio.md` (achado, risco, rota, decisão: corrigir agora / aceitar com
      motivo / falso positivo). Regra: **todo "Alto" e "Médio" real é corrigido ou aceito por escrito com motivo antes do
      piloto**; "Baixo" e "Informativo" vão para `erros-conhecidos.md` ou viram nota. A correção é commit pequeno por
      achado, com teste quando for código
- [ ] Segundo passe **manual** do que o ZAP não pega: um usuário de uma paróquia lendo dado de outra (já coberto por
      `TenantIsolamentoIT`, conferir que continua verde no servidor), IDOR em `/api/fotos/{id}` e `/api/alocacoes/{id}`,
      e o link público de convite/troca com token trocado ou repetido
- [ ] Registrar em `checklist-seguranca.md` a data do ZAP e o link do relatório

Cuidados:

- [ ] O scan ativo **cria e apaga dado** (e dispara e-mails): só com a paróquia fictícia do servidor de teste e o SMTP do
      servidor apontando para uma caixa sua ou para o Mailpit; nunca com o piloto. Confirmar o destino do SMTP antes
- [ ] O scan não pode derrubar o servidor de teste durante o teste da pessoa de fora: marcar o horário

Verificação (colar no PR): relatório do baseline e do scan autenticado, `Features/zap-relatorio.md` preenchido, nenhum "Alto"
sem decisão.

### Cartão 6 — Termo de uso e política de privacidade básicos

Problema: o sistema guarda nome, e-mail, telefone, foto, escalas, disponibilidade e dados financeiros da pastoral, e vai
a produção sem dizer isso a ninguém (a LGPD completa, com exportar e apagar dados, é da Fase 3). Decisão: textos
**básicos** em português claro, mostrados no primeiro acesso, e o back-end **registra qual versão** cada pessoa aceitou.

Ler: `model/Usuario`, `model/dto/MeResponseDTO`, `service/MeService`, `controller/MeController`, `service/AuditLogService`,
`service/conta/AtivacaoContaService` (primeiro acesso), migration `V16__usuario_telefone_aparencia.sql` (padrão de coluna
nova em `usuario`), teste `pastoral/MeIT`.

- [ ] **Textos** (não são código): `Features/termos-de-uso.md` e `Features/politica-de-privacidade.md`, escritos a partir do
      que o sistema realmente faz (lista de dados, para que servem, quem vê o quê — coordenação, padre, ADMIN —, por quanto
      tempo ficam, e-mail, que **não** há venda nem compartilhamento, cookies de sessão, contato para pedir
      correção/exclusão enquanto não houver a tela). A paróquia é a controladora; o texto traz o nome e o e-mail de
      contato dela (variável, não fixo). Revisão por alguém da paróquia e, idealmente, por quem entenda de LGPD, **antes**
      do piloto (ver Pendente/decisões). O front mostra o texto em páginas `/termos` e `/privacidade`, públicas
- [ ] `V24__usuario_termos.sql`: `usuario.termos_versao VARCHAR(20) NULL` e `usuario.termos_aceitos_em TIMESTAMP NULL`
      (sem `CHECK`; conta nova nasce sem aceite)
- [ ] Propriedade `servio.termos.versao` (ex.: `2027-02`; sobe quando os textos mudam)
- [ ] `GET /api/me` (e `PUT /api/me`) ganham `termosVersaoAtual` e `termosPendentes` (`true` se `termos_versao` é nulo ou
      diferente da versão atual); o front mostra a tela de aceite quando for `true`
- [ ] `POST /api/me/termos` `{ versao }` → 204: só aceita a **versão atual** (outra: 422 "Os termos mudaram: recarregue a
      página"), grava `termos_versao` e `termos_aceitos_em` (`Clock` do contexto), `AuditLog` `TERMOS_ACEITOS` (versão, sem
      texto nem dado pessoal). Idempotente. Conta de demonstração: o `POST` entra na regra que bloqueia escrita
- [ ] **O back-end não barra** nenhuma outra rota por falta de aceite (quem barra é o front); o padrão e a alternativa estão
      em Pendente/decisões
- [ ] Contas **antigas** (as do piloto, criadas antes desta sprint) aparecem com `termosPendentes = true` no primeiro login depois
      do deploy

Cuidados com testes existentes:

- [ ] `MeIT`: o JSON ganha `termosVersaoAtual` e `termosPendentes`; os asserts atuais valem; `MassAssignmentIT`: `termosVersao`
      no corpo do `PUT /api/me` é 400 (o aceite só entra por `POST /api/me/termos`)
- [ ] `OpenApiIT`: acrescentar `/api/me/termos`; o front roda `npm run gen:api`

Testes novos — `TermosIT`:

- [ ] Conta nova: `termosPendentes = true`, `termosVersaoAtual` = a da propriedade
- [ ] Aceitar a versão atual: 204, `termosPendentes = false`, `termos_aceitos_em` preenchida, uma linha `TERMOS_ACEITOS` no
      `AuditLog`; aceitar de novo: 204 sem duplicar a data
- [ ] Aceitar versão errada ou vazia: 422/400; sem login: 401; sem CSRF: 403
- [ ] Mudar `servio.termos.versao` (contexto com a propriedade nova): quem tinha aceitado a antiga volta a `true`
- [ ] A conta de demonstração não aceita (403) e continua vendo o sistema

### Cartão 7 — Runbook do piloto, lado back-end

Problema: o `runbook-piloto.md` descreve só como **subir** o piloto (túnel rápido e VM). Falta o que a pessoa que opera
precisa **durante** o piloto, e o que mudou nas sprints 1 a 6. Cartão de documentação (sem código).

Ler: `Features/runbook-piloto.md`, os cartões 1 a 6 desta sprint, `sprint-2.md` (reenviar ativação), `sprint-4.md` (horário
fixo e job de missas), `sprint-5.md` (trocas e avisos), `checklist-seguranca.md`.

- [ ] Reescrever a Parte 2 do runbook para o servidor de teste fixo (domínio, HTTPS, compose de produção, `scripts/deploy.sh`),
      mantendo as Opções A e B para quem for rodar na própria máquina, e remover o túnel rápido como caminho padrão
- [ ] Seções novas, cada uma com **o comando exato** e **como saber que deu certo**:
  - [ ] Ver se está de pé (`/healthz`, `docker compose ps`, logs do `app` sem segredo) e o que fazer se o `app` ficar `unhealthy`
  - [ ] Backup e restauração (diário, antes do deploy, onde fica a cópia fora do servidor, como restaurar num banco novo e como
        trocar pelo de produção), com a data do último teste real
  - [ ] Deploy e volta atrás (e por que o Flyway não desfaz migration)
  - [ ] Cadastro de membro e **reenvio da ativação** (sprint 2: limite de um por hora por conta, 20 cadastros por hora por
        coordenador); o que fazer se o e-mail de ativação não chegou (spam, e-mail errado, reenviar, última saída)
  - [ ] **Missas de domingo**: cadastrar o horário fixo, conferir que as 8 semanas apareceram, cancelar uma missa gerada sem ela
        voltar (sprint 4); o que fazer se o job de geração falhar (log, rodar de novo reiniciando o `app`)
  - [ ] Convites e trocas travados (sprint 1 e 5): reenviar convite, o que acontece quando o prazo vence, como a coordenação
        confere um pedido de troca
  - [ ] Esqueci a senha, conta bloqueada por tentativas (429, 15 minutos) e e-mail do usuário errado
  - [ ] **Pedido de dados ou de exclusão** (LGPD, enquanto não há tela): quem recebe, como exportar os dados de uma pessoa
        (consultas SQL prontas e revisadas) e como desativar a conta (`is_active`), com o prazo prometido na política
  - [ ] Girar segredos (senha do banco, do admin, do SMTP) e o que invalida (sessões)
  - [ ] Erros do piloto: onde registrar (lista única em `erros-conhecidos.md`) e a regra de "uma correção por semana, em dia
        fixo" da fase de 01/03 a 20/03
- [ ] `checklist-seguranca.md`: itens marcados com data e responsável à medida que os cartões fecham; o que sobrar vai para
      "riscos aceitos no piloto"
- [ ] Rodar o runbook **de ponta a ponta numa máquina limpa** (ou numa VM nova) seguindo só o texto: tudo o que precisou de
      ajuda fora do texto vira correção no runbook

## Contrato combinado (rotas e arquivos novos; ver `sprint-0.md`, seção 5)

> O "Mapa de endpoints" do front ainda não estava disponível quando este documento foi escrito: **nomes e formatos
> abaixo são a proposta do back-end** e precisam ser confirmados com o colaborador antes do cartão 6.

- [ ] `GET /actuator/health` (200 `{"status":"UP"}`, sem detalhe), `/actuator/health/liveness`, `/actuator/health/readiness`; `GET
      /healthz` no nginx (monitor externo)
- [ ] `GET /api/me` e `PUT /api/me`: `termosVersaoAtual`, `termosPendentes`; `POST /api/me/termos` `{ versao }` → 204; 422 versão
      desatualizada
- [ ] Páginas públicas do front: `/termos` e `/privacidade` (texto vem de `Features/termos-de-uso.md` e
      `Features/politica-de-privacidade.md`, revisados)
- [ ] Arquivos: `docker-compose.prod.yml`, `scripts/{backup,restaurar,testar-restauracao,deploy,fumaca,dados-teste}.sh`,
      `zap/`, `.github/workflows/zap.yml` (manual), `Features/zap-relatorio.md`
- [ ] Mensagens de erro que o front vai mostrar (versão dos termos mudou, aceite necessário)

## Fechamento da sprint

- [ ] Atualizar `Features/features-implementadas.md` e `Features/erros-conhecidos.md` com o que mudou (inclusive os riscos
      aceitos para o piloto e os achados do ZAP que ficaram)
- [ ] `checklist-seguranca.md` com cada item marcado, data e responsável, ou movido para "riscos aceitos no piloto" com motivo
- [ ] `runbook-piloto.md` reescrito e **testado do zero** (cartão 7)
- [ ] Resumo por cartão: o que mudou, testes novos, e qualquer coisa que ficou pendente ou decidida diferente do planejado
      (com o motivo)
- [ ] Conferir o "Pronto quando": uma pessoa de fora, no celular e sem ajuda, abre o endereço do servidor de teste, ativa a
      conta pelo e-mail, entra, aceita os termos e vê a própria escala; o backup foi restaurado e o ZAP não deixou
      "Alto" sem decisão
- [ ] Congelar a versão do piloto (tag no Git com o hash implantado) e avisar o colaborador

## Pendente/decisões

Dúvidas reais deste planejamento. Nenhuma foi decidida por palpite: a coluna "padrão proposto" é o que o código ou o
documento faz **se ninguém decidir diferente**, para não travar o cartão. As duas primeiras linhas **precisam de
decisão do usuário** e bloqueiam parte da sprint.

| Dúvida | Padrão proposto | Cartão |
|---|---|---|
| **Onde hospedar o servidor de teste e o piloto (servidor, domínio, custo mensal). Sem padrão definido; decidir até 01/02** | **Sem padrão: precisa de decisão.** Para não parar, o planejamento assume o que o runbook já descreve: a VM Oracle Always Free (Opção B), no mesmo `docker compose`. Outras opções para comparar: VPS barato (R$ 20 a 40 por mês, mais simples e com suporte), PaaS com contêiner (menos manutenção de servidor, custo por uso) e a própria máquina da paróquia (sem custo, mas depende de energia e de internet). Os cartões 1, 2 e 4 não dependem da escolha; o 3 e o 5 só chegam até scripts e documentação sem ela | 3, 5, 7 |
| **Repositório público ou privado (decidir até 15/02; padrão do cronograma: privado antes do piloto)** | Privado antes do piloto. Efeitos a decidir **juntos**: (a) o runbook clona o repositório público na VM: privado exige chave de deploy ou imagem num registro privado; (b) a cota gratuita de minutos do GitHub Actions é limitada em repositório privado, e o `./mvnw verify` leva ~20 minutos e a fila já está longa: conferir a cota antes de mudar; (c) varredura de segredos no histórico inteiro **antes** (cartão 4); (d) a revenda em vista pede o repositório fechado | 3, 4 |
| Domínio e HTTPS: túnel nomeado da Cloudflare ou Caddy/nginx com Let's Encrypt? | Túnel nomeado da Cloudflare com um domínio seu (o TLS fica na Cloudflare e não precisa abrir 80/443 na VM), por já ser a ferramenta do runbook. Caddy com Let's Encrypt é a alternativa sem depender de terceiro, abrindo 80/443. O túnel **rápido** (URL que muda) sai do caminho padrão: ele quebra os links dos e-mails já enviados | 3 |
| Indicador de e-mail do Actuator: deixar ligado? | Desligado (`management.health.mail.enabled=false`): SMTP lento não pode tirar o sistema do ar nem fazer o Docker reiniciar o `app`. Falha de e-mail já aparece na fila (`email_saida`, `FALHOU`) | 1 |
| Cliente HTTP para o `healthcheck` dentro da imagem `eclipse-temurin:25-jre` | Usar o que a imagem já tiver (`curl` ou `wget`); se nenhum, um comando `java` de uma linha em vez de instalar pacote (cada pacote novo entra no scan do trivy e pode trazer CVE) | 1 |
| Reiniciar o `app` quando ficar `unhealthy` | `restart: unless-stopped` só reinicia quem **caiu**. Padrão: não reiniciar sozinho no piloto (ver o `unhealthy` no monitor e agir); `autoheal` é um contêiner a mais e fica para depois, se o piloto mostrar necessidade | 1 |
| Monitor externo de disponibilidade (alerta se cair) | Um monitor gratuito de uptime olhando `https://<domínio>/healthz`, avisando por e-mail a quem opera. Sem ferramenta paga nem painel nesta sprint | 1, 3 |
| Onde guardar a cópia do backup **fora do servidor** | Um armazenamento de objetos gratuito ou barato (o da própria Oracle ou um bucket pequeno), via `rclone`, **criptografado** (`rclone crypt`): o dump tem dados pessoais e hashes de senha. Sem cópia fora do servidor, o backup não protege de perder a VM | 2 |
| Frequência, retenção e objetivo de recuperação | Diário às 03:00 mais um antes de cada deploy; 14 diários e 4 semanais; no piloto, aceitar perder até 24 horas de dados e voltar em até 4 horas. Revisar depois do piloto | 2 |
| Teste de restauração roda no CI? | Não por padrão: roda à mão e a saída vai no PR, mais um `workflow_dispatch` opcional. A fila do CI já está longa e o teste sobe Postgres e aplicação | 2 |
| Quem roda e tria o ZAP, e onde | Quem opera o servidor de teste, **só** contra o servidor de teste com dados fictícios e SMTP controlado; o back-end corrige. Nunca contra o piloto com dados reais | 5 |
| OpenAPI ligado no servidor de teste só para o ZAP | Ligar só durante o scan e desligar depois (`OPENAPI_ENABLED`), conferido no checklist; em produção fica sempre desligado | 4, 5 |
| O login derruba o scan do ZAP (429 depois de 5 falhas) | Excluir o login do fuzz e testá-lo à mão; **não** afrouxar o rate limit nem criar exceção no código só para o scanner | 5 |
| "Alto" e "Médio" do ZAP no piloto | Todo Alto e Médio **real** é corrigido ou aceito por escrito (com motivo) antes de 15/02; Baixo e Informativo vão para `erros-conhecidos.md` | 5 |
| Quem redige e revisa o termo e a política de privacidade | Texto básico escrito pelo time a partir do que o sistema faz, **revisado por alguém da paróquia** e, se possível, por quem entenda de LGPD, antes do piloto. A paróquia é a controladora; o texto traz o contato dela. Sem isso, o piloto roda com os dados de 2 a 3 testadores que sabem o que está acontecendo | 6 |
| O aceite dos termos bloqueia a API? | Não: o back-end só registra a versão aceita; quem mostra a tela e impede de seguir é o front. Bloquear no back-end (403 em tudo menos `/api/me` e `/api/auth/**` até aceitar) fica para depois do piloto, se a paróquia exigir | 6 |
| Mudou o texto: precisa aceitar de novo? | Sim: sobe `servio.termos.versao` e todos voltam a `termosPendentes = true` no próximo acesso | 6 |
| Direitos do titular (exportar e apagar dados) antes da Fase 3 | Atendidos **à mão**, por pedido por e-mail, com prazo prometido na política (sugestão: 15 dias) e passos no runbook; a tela é da Fase 3 | 6, 7 |
| Dados reais do piloto no servidor de teste | Não: o servidor de teste usa só a paróquia fictícia do `scripts/dados-teste.sh`. O piloto real roda em outra instância (ou outro banco) depois do ZAP e do checklist | 3, 5 |
| Carnaval (segunda 08/02 e terça 09/02) e capacidade da sprint | Contar a sprint como uma semana e meia de trabalho. Se faltar tempo, **cortar nesta ordem**: dados de teste (`dados-teste.sh`), CI opcional do restore, páginas separadas do ZAP para as rotas públicas; **não** cortar backup restaurado, HTTPS, health check nem o primeiro ZAP | todos |
| Congelamento do código na Semana Santa (21/03 a 28/03) | Sem mudança: só correção de erro que impede o uso. O deploy desta sprint é o que o piloto usa até lá, com a "uma correção por semana, em dia fixo" de 01/03 a 20/03 | 3, 7 |
| Nomes das rotas e dos campos (sem o "Mapa de endpoints") | Proposta no "Contrato combinado"; confirmar com o colaborador antes de começar o cartão 6 | 1, 6 |

## Fora desta sprint

- [ ] Ajustes de acessibilidade, teste completo no celular dos coordenadores e Playwright dos fluxos novos: trabalho do
      colaborador
- [ ] LGPD completa (tela de exportar e de apagar dados), backup incremental/contínuo (WAL), alta disponibilidade e mais de uma
      instância, Redis para sessão (só se houver várias instâncias), observabilidade (métricas, painel, alertas além do
      uptime), logs estruturados, perfil `prod` separado (`application-prod.properties`)
- [ ] Relatórios, notificação push, preparação para revenda: Fase 3 (29/03 a 30/04)

Ver também: `sprint-5.md`, `sprint-4.md`, `sprint-2.md`, `sprint-1.md`, `sprint-0.md`, `features-futuras.md`,
`checklist-seguranca.md`, `runbook-piloto.md`.
