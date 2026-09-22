# Features futuras

Ordem sugerida por prioridade. Marcadas como (P1) bloqueia o uso, (P2) importante, (P3) desejável.

## Segurança e acesso
- (P1) **Bootstrap do primeiro ADMIN**: seed via migration/variáveis de ambiente ou endpoint de setup, já que hoje ninguém consegue logar.
- (P1) **Autorização por perfil**: restringir rotas por role (ADMIN, COORDENADOR, PADRE, SERVIDOR), por exemplo só ADMIN/COORDENADOR criam usuários e celebrações; SERVIDOR só edita as próprias indisponibilidades e pedidos de troca.
- (P1) **Isolamento por paróquia (multi-tenant)**: toda consulta filtrada pela paróquia do usuário logado.
- (P1) **Login com sessão no servidor**: Spring Session JDBC (sessões no próprio PostgreSQL), cookie `HttpOnly`/`Secure`/`SameSite=Lax`, CSRF para o front React, rate limit no login e encerramento imediato das sessões ao mudar perfil/senha. Redis só se houver várias instâncias no futuro.
- (P2) **Login sem ambiguidade**: identificar o usuário por e-mail + paróquia (ou e-mail único global).
- (P2) **Troca/recuperação de senha** e endpoint `GET /api/me` do usuário autenticado.

## Regras de negócio da escala
- (P1) **Fluxo de PedidoTroca**: transições de `StatusTroca` (ABERTO → ACEITO/RECUSADO/CANCELADO), só o solicitante cancela, só o destinatário aceita/recusa, e a troca efetiva a `Alocacao`.
- (P1) **Validar alocações**: usuário indisponível (`Indisponibilidade`) ou com compromisso (`CompromissoAgenda`) no horário não pode ser alocado; não alocar acima do número de vagas; usuário e vaga da mesma paróquia; usuário com a `Funcao` exigida.
- (P2) **Geração automática da escala** para uma celebração/período, respeitando funções, disponibilidade e rodízio justo.
- (P2) **Validações de datas**: `dataFim >= dataInicio` em indisponibilidade, sem sobreposição.
- (P3) **Relatórios**: frequência por servidor, escala mensal, exportação PDF/planilha.

## Auditoria e notificações
- (P2) **Gravar `AuditLog`** nas ações relevantes (criar/alterar/excluir alocação, troca, usuário) com o usuário autenticado.
- (P2) **Notificações por e-mail** (via `Notificador`): nova alocação, pedido de troca, resposta à troca, lembrete antes da celebração; configuração de SMTP por ambiente.
- (P3) Outros canais (WhatsApp/push) atrás da mesma interface `Notificador`.

## API e qualidade
- (P2) **Paginação e filtros** nas listagens (hoje retornam tudo).
- (P2) **Testes**: unitários dos services (use `/gen-tests`), integração com Testcontainers/PostgreSQL e testes de segurança.
- (P2) **Documentação OpenAPI/Swagger** (springdoc).
- (P3) Actuator (`/health`) e logs estruturados; healthcheck do container `app` no compose.
- (P3) CI (build + testes) e perfil `prod` separado (`application-prod.properties`, sem senhas padrão).
- (P3) Front-end (web/mobile) para servidores e coordenação.

Ver também: `features-implementadas.md` e `erros-conhecidos.md`.
