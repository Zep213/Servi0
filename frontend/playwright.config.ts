import { defineConfig } from '@playwright/test';

/**
 * Roda contra o `docker compose` (serviço `web`, porta 80, com Mailpit) já no ar — não sobe nada
 * sozinho. Ver `e2e/global-setup.ts` para os dados de partida e `.github/workflows/e2e.yml` para
 * como a CI prepara a stack. `workflow_dispatch` manual, não a cada push.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['html', { open: 'never' }]],
  globalSetup: './e2e/global-setup.ts',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
});
