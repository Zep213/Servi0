# Runbook: piloto fechado (Cloudflare Tunnel + Gmail SMTP)

Passo a passo para publicar o Servio para um piloto fechado (2-3 testadores), rodando na própria
máquina. Pré-requisitos pessoais (fazer antes de começar):

1. Gerar uma **senha de app do Gmail** na conta que vai enviar os e-mails (Conta Google →
   Segurança → Verificação em duas etapas → Senhas de app).
2. Criar uma conta na **Cloudflare** (gratuita) e instalar o `cloudflared` na máquina que vai
   rodar o `docker-compose` (`cloudflared` não entra no `docker-compose.yml` do projeto —
   roda à parte, direto na máquina).

## 1. Configurar o `.env.prod`

Use um arquivo **separado** do `.env` de dev (que fica com o banco local de desenvolvimento) —
o deploy do piloto passa esse arquivo explicitamente pro `docker compose`:

```
cp .env.prod.example .env.prod
```

Preencha no `.env.prod`:
- `POSTGRES_PASSWORD`, `SERVIO_ADMIN_SENHA`: senhas fortes e únicas (nunca os valores do
  `.env.example`/`.env.prod.example`).
- `SERVIO_ADMIN_EMAIL`, `SERVIO_ADMIN_NOME`, `SERVIO_PAROQUIA_NOME`: dados reais.
- `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`: o Gmail e a senha de app do passo 1.
- `SERVIO_MAIL_FROM`: `Servio <mesmo-email-do-SPRING_MAIL_USERNAME>`.
- `SERVIO_FRONT_URL`: deixe em branco por enquanto — só dá pra saber depois do passo 3.

Se você estiver usando um Postgres nativo na porta 5432 para o dev local, deixe o `DB_PORT`
do `.env.prod` em 5433 (já é o padrão do `.env.prod.example`) para não conflitar.

## 2. Subir a aplicação

```
docker compose --env-file .env.prod up -d --build
```

Confirme que `db`, `app` e `web` estão saudáveis (`docker compose --env-file .env.prod ps`).
O `mailpit` também sobe (faz parte do compose único do projeto) mas não é usado em produção —
pode ignorar. Sempre use `--env-file .env.prod` nos comandos deste runbook, senão o Compose
volta a ler o `.env` de dev.

## 3. Abrir o túnel

Com a aplicação respondendo em `http://localhost:${WEB_PORT:-80}`, rode um túnel rápido:

```
cloudflared tunnel --url http://localhost:80
```

Isso imprime uma URL pública `https://algo-aleatorio.trycloudflare.com`. Copie essa URL.

## 4. Fechar o `SERVIO_FRONT_URL` e reiniciar

Edite o `.env.prod`, preencha `SERVIO_FRONT_URL` com a URL do passo 3, e reinicie só o `app`
(os links nos e-mails de convite usam essa variável):

```
docker compose --env-file .env.prod up -d app
```

Mantenha o `cloudflared` rodando no terminal (ou como serviço) durante todo o piloto — se ele
cair, a URL muda e os links antigos nos e-mails já enviados param de funcionar.

## 5. Teste fumaça

1. Acesse a URL pública, faça login com o admin criado no passo 1.
2. Cadastre 1 pastoral e os participantes reais do piloto (com e-mails de verdade).
3. Rode um sorteio/escalação de verdade.
4. Confirme que o e-mail de convite chega numa caixa real (não localhost/Mailpit) e que o link
   de dentro do e-mail abre a URL pública e funciona.

## Encerrar o piloto / voltar pro ambiente de dev

```
docker compose --env-file .env.prod down
Ctrl+C no terminal do cloudflared
```

Veja também: `checklist-seguranca.md` (itens a conferir antes de abrir para a paróquia toda,
além do piloto fechado).
