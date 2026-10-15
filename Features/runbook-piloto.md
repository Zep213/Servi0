# Runbook: piloto fechado (Cloudflare Tunnel + Gmail SMTP)

Passo a passo para publicar o Servio para um piloto fechado (2-3 testadores). Duas opções de
onde rodar — os passos 1 e 5 (env e teste fumaça) são iguais nas duas; só a Parte 2 muda:

- **Opção A** — na sua própria máquina. Mais simples, zero conta nova, mas depende da máquina
  ficar ligada e com internet o tempo todo.
- **Opção B** — numa VM grátis da Oracle Cloud (Always Free). Um pouco mais de passos pra
  configurar, mas fica no ar 24/7 sem depender do seu notebook, e é o mesmo `docker-compose`
  de sempre — só muda onde ele roda.

Pré-requisitos pessoais (fazer antes de começar, valem pras duas opções):

1. Gerar uma **senha de app do Gmail** na conta que vai enviar os e-mails (Conta Google →
   Segurança → Verificação em duas etapas → Senhas de app). *(Adiado por enquanto — vai usar o
   e-mail da Pascom depois.)*
2. `cloudflared` não entra no `docker-compose.yml` do projeto — roda à parte, direto na máquina
   (ou na VM). Não precisa de conta na Cloudflare para o túnel rápido (`--url`) usado aqui.

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
- `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`: o Gmail e a senha de app do passo 1 (pode
  deixar em branco por enquanto, como fizemos — sem isso só o envio de e-mail não funciona).
- `SERVIO_MAIL_FROM`: `Servio <mesmo-email-do-SPRING_MAIL_USERNAME>`.
- `SERVIO_FRONT_URL`: deixe em branco por enquanto — só dá pra saber depois de abrir o túnel.

Se você estiver usando um Postgres nativo na porta 5432 para o dev local, deixe o `DB_PORT`
do `.env.prod` em 5433 (já é o padrão do `.env.prod.example`) para não conflitar — vale também
dentro da VM da Opção B, caso um dia rode mais de uma coisa lá.

## 2. Subir a aplicação

### Opção A — na sua máquina

```
docker compose --env-file .env.prod up -d --build
```

Confirme que `db`, `app` e `web` estão saudáveis (`docker compose --env-file .env.prod ps`).
O `mailpit` também sobe (faz parte do compose único do projeto) mas não é usado em produção —
pode ignorar. Sempre use `--env-file .env.prod` nos comandos deste runbook, senão o Compose
volta a ler o `.env` de dev. Vá direto para o passo 3.

### Opção B — VM grátis na Oracle Cloud (Always Free)

**2B.1 Criar a conta e a VM** (só você — precisa de cartão pra verificação, mas o Always Free
não cobra):
1. Crie a conta em Oracle Cloud (busque "Oracle Cloud Free Tier" / "sign up").
2. No console, crie uma instância: Shape `VM.Standard.A1.Flex` (Ampere, parte do Always Free —
   2 OCPU / 12 GB já é de sobra pro piloto), imagem Ubuntu, e adicione sua chave SSH pública
   na criação (gere uma com `ssh-keygen` se não tiver).
3. Anote o IP público que a instância recebe.

**2B.2 Conectar e instalar o Docker na VM**

```
ssh ubuntu@<ip-da-vm>
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
# saia e reconecte (ssh de novo) pro grupo "docker" valer
```

**2B.3 Levar o código e o `.env.prod` pra VM**

O repositório é público, então dá pra clonar direto (sem configurar chave de deploy):

```
git clone https://github.com/Zep213/Servi0.git
cd Servi0
```

O `.env.prod` **nunca vai pelo git** (está no `.gitignore`) — copie separado, da sua máquina
pra VM:

```
scp .env.prod ubuntu@<ip-da-vm>:~/Servi0/.env.prod
```

**2B.4 Instalar o `cloudflared` na VM**

```
curl -fsSL -o cloudflared.deb https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-arm64.deb
sudo apt install ./cloudflared.deb
```

(`arm64` porque o Always Free é Ampere/ARM — não o `amd64` que usamos no notebook.)

**2B.5 Subir a aplicação**

```
docker compose --env-file .env.prod up -d --build
docker compose --env-file .env.prod ps
```

Depois disso, vá para o passo 3 — mas rodando os comandos **na VM** (via SSH), não na sua
máquina.

**2B.6 Deixar o `cloudflared` sempre no ar**

Numa VM você não fica com um terminal aberto o tempo todo, então registre o túnel como serviço
em vez de rodar direto (senão ele morre quando a conexão SSH cair):

```
sudo tee /etc/systemd/system/cloudflared-servio.service > /dev/null <<'EOF'
[Unit]
Description=Cloudflare quick tunnel - Servio
After=network.target docker.service

[Service]
ExecStart=/usr/local/bin/cloudflared tunnel --url http://localhost:80
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now cloudflared-servio
```

Pegue a URL gerada no log do serviço: `journalctl -u cloudflared-servio -f` (espere aparecer a
linha com `trycloudflare.com`, depois `Ctrl+C` pra sair do log — o serviço continua rodando).

**Atenção**: esse é o túnel "rápido" (sem conta/domínio), então toda vez que o serviço reiniciar
(reboot da VM, queda de conexão) a URL muda. Pra um domínio fixo, o passo seria criar um túnel
nomeado com um domínio seu adicionado à Cloudflare — fica pra quando migrar do piloto pra algo
maior, não é necessário agora.

## 3. Abrir o túnel (Opção A) / Conferir a URL (Opção B)

Se escolheu a Opção A, com a aplicação respondendo em `http://localhost:${WEB_PORT:-80}`, rode
um túnel rápido:

```
cloudflared tunnel --url http://localhost:80
```

Isso imprime uma URL pública `https://algo-aleatorio.trycloudflare.com`. Copie essa URL (na
Opção B, já peguei essa URL no passo 2B.6).

## 4. Fechar o `SERVIO_FRONT_URL` e reiniciar

Edite o `.env.prod` (na máquina ou na VM, onde ele estiver), preencha `SERVIO_FRONT_URL` com a
URL do passo 3, e reinicie só o `app` (os links nos e-mails de convite usam essa variável):

```
docker compose --env-file .env.prod up -d app
```

Na Opção A, mantenha o `cloudflared` rodando no terminal (ou como serviço, igual ao 2B.6)
durante todo o piloto — se ele cair, a URL muda e os links antigos nos e-mails já enviados
param de funcionar. Na Opção B isso já está coberto pelo serviço systemd.

## 5. Teste fumaça

1. Acesse a URL pública, faça login com o admin criado no `.env.prod`.
2. Cadastre 1 pastoral e os participantes reais do piloto (com e-mails de verdade, ou crie as
   contas direto em Membros com senha inicial, sem depender de e-mail).
3. Rode um sorteio/escalação de verdade.
4. Se o SMTP já estiver configurado, confirme que o e-mail de convite chega numa caixa real
   (não localhost/Mailpit) e que o link de dentro do e-mail abre a URL pública e funciona.

## Conta de demonstração (somente leitura)

Para deixar alguém experimentar o sistema sem risco de estragar os dados reais do piloto,
preencha `SERVIO_DEMO_EMAIL`/`SERVIO_DEMO_SENHA` (e opcionalmente `SERVIO_DEMO_NOME`) no
`.env.prod` e reinicie o `app`:

```
docker compose --env-file .env.prod up -d app
```

No próximo início, essa conta é criada automaticamente como ADMIN na mesma paróquia (vê tudo:
pastorais, escalas, financeiro, auditoria), mas qualquer tentativa de alterar algo (criar,
editar, excluir) é bloqueada pelo `SomenteLeituraFilter` — a pessoa só navega e olha. Passe a
URL pública e essas credenciais pra quem for só dar uma olhada, sem usar a conta real do ADMIN.
Deixe as duas variáveis em branco se não quiser essa conta.

Tem também a versão com visão de **membro** (perfil SERVIDOR, entra como MEMBRO da primeira
pastoral cadastrada): preencha `SERVIO_DEMO_MEMBRO_EMAIL`/`SERVIO_DEMO_MEMBRO_SENHA` (e
`SERVIO_DEMO_MEMBRO_NOME` se quiser) e reinicie o `app` do mesmo jeito. Mesma trava de somente
leitura — dá pra mostrar as duas pontas do sistema (quem coordena vs. quem é escalado) sem
nenhuma das duas poder alterar nada.

## Encerrar o piloto / voltar pro ambiente de dev

Opção A:
```
docker compose --env-file .env.prod down
Ctrl+C no terminal do cloudflared
```

Opção B (na VM, via SSH):
```
docker compose --env-file .env.prod down
sudo systemctl stop cloudflared-servio
```

Veja também: `checklist-seguranca.md` (itens a conferir antes de abrir para a paróquia toda,
além do piloto fechado).
