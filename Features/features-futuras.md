# Features futuras

Ordem sugerida por prioridade. Marcadas como (P1) bloqueia o uso, (P2) importante, (P3) desejável.
A partir da sprint 1, a ordem passa a seguir o cronograma combinado com o front-end — ver `sprint-0.md`.
Cronograma completo (fonte: "Servio — Cronograma do MVP Pascom", 13/10/2026): 7 sprints de 2 semanas (26/10 a 14/02), piloto de 15/02 a 21/03, código congelado na Semana Santa (21/03 a 28/03). Pronto = testes no CI, PR revisado pelo outro, tela conferida no celular e `Features/` atualizada. Só a sprint 1 tem cartões detalhados (`sprint-1.md`); as sprints 2 a 7 abaixo ainda são escopo, sem cartões.

## Sprint 1 (26/10 a 08/11)
- (P1) Fila de e-mails.
- (P1) Lembrete sem invalidar o link do convite.
- (P1) Login por e-mail ou telefone (`POST /api/auth/login` passa a receber `login`, não `email`).
- (P1) Sessão por cargo.
- (P1) `GET/PUT /api/me` com `telefone`, `aparencia`, `pedirTelefone` e `pastorais[].pendencias`.
- (P1) Casca nova no front (barra lateral, sininho, avatar, seletor de aparência, calendário do mês).

## Sprint 2 — Ativação e recuperação (09/11 a 22/11)
- Back-end: cadastro de membro sem senha, numa única operação; link de ativação (7 dias); esqueci a senha (link de 1 hora); e-mail de senha alterada e "Proteger minha conta"; mudança de papel encerra sessões; foto de perfil opcional (upload com limite de tamanho).
- Front-end: telas Entrar, Ativar conta, Esqueci a senha; e-mail de senha alterada.
- Pronto quando: um membro novo entra sem que ninguém saiba a senha dele.

## Sprint 3 — Habilidades e membros (23/11 a 06/12)
- Back-end: regra HABILIDADE no catálogo; tirar o filtro "só MEMBRO"; sortear primeiro a função mais apertada; endpoint de membro com status de ativação.
- Front-end: Membros (busca, filtros), Detalhe do membro, Adicionar pessoa.
- Pronto quando: o sorteio só escolhe quem tem a habilidade da vaga.

## Sprint 4 — Agenda e celebrações (07/12 a 20/12)
- Back-end: horário fixo da missa de domingo (a Pascom cobre uma por domingo); job que mantém 8 semanas geradas; coordenação cria eventos; prazo do convite limitado pela hora do evento.
- Front-end: agenda por semana no celular e calendário do mês no computador; Escala da missa (Substituir, justificativa); Escolher pessoa; Novo evento; Configurações com prazos.
- Pronto quando: as missas de janeiro aparecem sozinhas e o prazo nunca passa da missa.
- Pausa de Natal e Ano-Novo: 21/12 a 03/01, sem entregas.

## Sprint 5 — Troca e Início (04/01 a 17/01)
- Back-end: fluxo de troca com estados, revalidação das regras, aviso à coordenação e expiração (24 horas antes do evento); tabela de avisos do app com lido e não lido; `GET /api/me/inicio`; página pública para aceitar a troca pelo link do e-mail, igual à do convite.
- Front-end: Início do membro, Escalas, Pedir troca, Disponibilidade, sininho e tela de Avisos.
- Pronto quando: uma troca aceita muda a escala e o coordenador recebe o aviso.

## Sprint 6 — Gestão e financeiro (18/01 a 31/01)
- Back-end: auditoria nas ações de escala, membros e financeiro; financeiro sem edição (estorno); pedido de reunião.
- Front-end: Início da gestão, Mais, Financeiro, Reuniões, Pedir reunião.
- Pronto quando: todo lançamento e toda alteração de escala aparecem na auditoria.
- Risco: com capacidade menor que a prevista, esta sprint sai do MVP e vai para depois do piloto (a escala é o que importa para a Pascom).

## Sprint 7 — Estabilização (01/02 a 14/02)
- Back-end: checklist de segurança (HTTPS, backup restaurado, ZAP); health check; deploy em servidor de teste; termo de uso e privacidade básico.
- Front-end: ajustes de acessibilidade; teste completo no celular dos coordenadores; Playwright dos fluxos novos.
- Pronto quando: uma pessoa de fora usa o sistema no celular sem ajuda.

## Piloto e Fase 3
- 15/02 a 28/02: piloto em paralelo com o jeito atual (planilha ou WhatsApp); erros numa lista única.
- 01/03 a 20/03: a escala sai só pelo Servio; uma correção por semana, em dia fixo.
- 21/03 a 28/03 (Semana Santa): código congelado, só correção de erro que impede o uso. Carnaval (09/02) e Páscoa (28/03/2027) foram calculados no cronograma; conferir no calendário da paróquia.
- 29/03 a 30/04: retrospectiva e escolha da ordem da Fase 3 — telas do padre (agenda e painel), outras pastorais, LGPD completo (exportar e apagar dados), relatórios, notificação push e preparação para revenda.

## Decisões pendentes (do cronograma)
| Decisão | Decidir até | Padrão / decidido |
|---|---|---|
| Telefone das contas antigas: obrigatório ou pode pular? | 26/10 | Decidido em 14/10: pode pular, com lembrete |
| Textos dos e-mails de ativação, recuperação e senha alterada | 09/11 | Rascunhos no protótipo (fileira E-mails); falta revisar |
| Mostrar a cidade no e-mail de senha alterada (exige serviço externo de IP)? | 09/11 | Não mostrar (só o tipo de aparelho) |
| "Técnico" também como habilidade na escala (som, telão)? | 23/11 | Só como cargo |
| Agenda no computador: calendário do mês ou lista larga? | 07/12 | Decidido em 14/10: calendário do mês com painel ao lado |
| Página pública para aceitar a troca pelo link do e-mail? | 04/01 | Decidido em 14/10: sim, igual à do convite |
| Quanto tempo antes do evento o pedido de troca expira? | 04/01 | Decidido em 14/10: 24 horas antes |
| Onde hospedar (servidor, domínio, custo mensal) | 01/02 | Sem padrão: precisa de decisão |
| Repositório continua público com a revenda em vista? | 15/02 | Privado antes do piloto |

Riscos do cronograma: e-mails caindo no spam (configurar SPF, DKIM e DMARC já na sprint 1); membros que não ativam a conta (ativar junto numa reunião da Pascom antes do piloto); uma pessoa por camada (revisão cruzada de PR).

## Segurança e acesso
- (P2) **Recuperação de senha** (esqueci minha senha) — a troca autenticada (`POST /api/me/senha`) já existe.
- (P2) **Onboarding de novas paróquias**: hoje só existe a paróquia criada pelo `BootstrapAdmin` na primeira subida; `ParoquiaController` só expõe `GET`/`PUT /api/paroquias/minha`. Se o produto for atender várias paróquias de forma contínua (não só a inicial), precisa de um fluxo de criação (provavelmente restrito a um super-admin fora do escopo de tenant).
- (P3) Redis para sessão só se houver várias instâncias da aplicação no futuro (hoje Spring Session JDBC no próprio Postgres é suficiente).

## Regras de negócio da escala
- (P1) **Fluxo de PedidoTroca**: transições de `StatusTroca` (ABERTO → ACEITO/RECUSADO/CANCELADO), só o solicitante cancela, só o destinatário aceita/recusa, e a troca efetiva a `Alocacao`.
- (P1) **Validar alocações**: usuário indisponível (`Indisponibilidade`) ou com compromisso (`CompromissoAgenda`) no horário não pode ser alocado; não alocar acima do número de vagas; usuário e vaga da mesma paróquia; usuário com a `Funcao` exigida.
- (P2) **Geração automática da escala** para uma celebração/período, respeitando funções, disponibilidade e rodízio justo.
- (P2) **Validações de datas**: `dataFim >= dataInicio` em indisponibilidade, sem sobreposição.
- (P3) **Relatórios**: frequência por servidor, escala mensal, exportação PDF/planilha.

## Auditoria e notificações
- (P2) **Gravar `AuditLog`** nas demais ações relevantes (criar/alterar/excluir alocação, troca, usuário) com o usuário autenticado — hoje só login (sucesso/falha) é auditado.
- (P2) **Notificações por e-mail** (via `Notificador`): nova alocação, pedido de troca, resposta à troca, lembrete antes da celebração; configuração de SMTP por ambiente.
- (P3) Outros canais (WhatsApp/push) atrás da mesma interface `Notificador`.

## API e qualidade
- (P2) **Filtros** nas listagens (a paginação já está implementada em todos os recursos tenant; falta filtrar por campos como data, status, perfil etc.).
- (P2) **Testes unitários dos services** (`/gen-tests`) — hoje a cobertura é toda via testes de integração (`*IT`).
- (P2) **Documentação OpenAPI/Swagger** (springdoc).
- (P3) Logs estruturados; healthcheck do container `app` no compose (a rota `/actuator/health` já está liberada no `SecurityConfig` e a dependência `spring-boot-starter-actuator` já foi adicionada; falta o healthcheck no `docker-compose.yml`).
- (P3) Perfil `prod` separado (`application-prod.properties`, sem senhas padrão) — CI (build + testes + scan) já existe.
- (P3) Front-end (web/mobile) para servidores e coordenação.
- (P3) OWASP ZAP contra staging antes do go-live (ver `checklist-seguranca.md`).

Ver também: `features-implementadas.md` e `erros-conhecidos.md`.
