# Checklist de segurança antes de ir para produção

Itens a conferir antes do primeiro deploy real (e depois de qualquer mudança grande na infra ou na segurança).

- [ ] **HTTPS** obrigatório na frente da aplicação (nginx/proxy), com redirecionamento de HTTP para HTTPS.
- [ ] **X-Forwarded-For** configurado corretamente no proxy, batendo com `server.forward-headers-strategy=native` (essencial para o rate limit do login usar o IP real do cliente, não o do proxy).
- [ ] **Senhas do `.env`** trocadas para valores fortes e únicos em produção (não usar os padrões `servio`/`servio` do compose).
- [ ] **Porta do banco fechada**: Postgres não pode ficar exposto publicamente (hoje já mapeia só em `127.0.0.1`; em produção, nem isso — acesso só pela rede interna).
- [ ] **Backup testado**: não basta ter backup, precisa já ter restaurado um pelo menos uma vez para confirmar que funciona.
- [ ] **CI verde**: pipeline (`ci.yml`) passando — build, testes, scan de segredos (gitleaks) e scan de imagem (trivy) sem falhas críticas/altas.
- [ ] **Logs sem segredos**: conferir que nenhum log (aplicação, nginx, docker) imprime senha, hash, token de sessão ou CSRF.
- [ ] **ZAP** (OWASP ZAP) rodado contra o ambiente de staging pelo menos uma vez antes do go-live.

Ver também: `features-implementadas.md`, `features-futuras.md` e `erros-conhecidos.md`.
