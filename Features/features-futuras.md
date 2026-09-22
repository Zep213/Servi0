# Features futuras

Ordem sugerida por prioridade. Marcadas como (P1) bloqueia o uso, (P2) importante, (P3) desejável.

## Segurança e acesso
- (P1) **Impedir que um COORDENADOR crie/promova alguém a `ADMIN`**: a rota já exige ADMIN/COORDENADOR (RBAC feito na etapa 3), mas o valor de `perfil` no request não é validado contra quem está chamando.
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
- (P3) Actuator (`/health`) e logs estruturados; healthcheck do container `app` no compose (a rota `/actuator/health` já está liberada no `SecurityConfig`, falta adicionar a dependência).
- (P3) Perfil `prod` separado (`application-prod.properties`, sem senhas padrão) — CI (build + testes + scan) já existe.
- (P3) Front-end (web/mobile) para servidores e coordenação.
- (P3) OWASP ZAP contra staging antes do go-live (ver `checklist-seguranca.md`).

Ver também: `features-implementadas.md` e `erros-conhecidos.md`.
