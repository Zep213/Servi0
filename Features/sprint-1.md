# Servio — Sprint 1

**Período:** 26/10 a 08/11/2026 · **Back-end:** Zep · **Front-end:** colaborador

> Esqueleto gerado a partir do combinado em `sprint-0.md`. Falta detalhar tarefas, critérios de aceite e o quadro (GitHub Projects/Trello) antes de começar a codar — combinar com o front-end antes de 26/10 (ver `sprint-0.md`, seção 5).

## Escopo

- [ ] Fila de e-mails
- [ ] Lembrete sem invalidar o link do convite
- [ ] Login por e-mail ou telefone
- [ ] Sessão por cargo
- [ ] `/api/me` (campos novos) e `/api/auth/login` (campo `login`)
- [ ] Casca nova no front (barra lateral, sininho, avatar, seletor de aparência, calendário do mês)

## Contrato combinado (ver `sprint-0.md`, seção 5)

- [ ] `GET /api/me`: campos novos `telefone`, `aparencia`, `pedirTelefone` e `pastorais[].pendencias`
- [ ] `PUT /api/me`: `{ telefone?, aparencia?, pularTelefone? }`; 409 para telefone já usado
- [ ] `POST /api/auth/login`: campo do formulário muda de `email` para `login`
- [ ] Mensagens de erro do front (telefone inválido, telefone em uso, login inválido, muitas tentativas)

## Pendente de detalhar

- [ ] Critérios de aceite de cada item do escopo
- [ ] Quebra em cartões (um branch por cartão)
- [ ] Link do protótipo e do mapa de endpoints (ainda não recebidos)

Ver também: `sprint-0.md`.
