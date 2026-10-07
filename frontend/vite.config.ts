import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';
import { configDefaults } from 'vitest/config';
import { VitePWA } from 'vite-plugin-pwa';

// Em desenvolvimento, /api vai para o backend na 8080: mesma origem, sem CORS.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'prompt',
      includeAssets: ['icone.svg', 'favicon.ico', 'apple-touch-icon-180x180.png'],
      manifest: {
        name: 'Servio',
        short_name: 'Servio',
        description: 'Escalas e gestão das pastorais da paróquia.',
        lang: 'pt-BR',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        // Cores neutras: nada de accent de pastoral (muda por pastoral), só o fundo do tema.
        background_color: '#f7f7f8',
        theme_color: '#f7f7f8',
        icons: [
          { src: 'pwa-64x64.png', sizes: '64x64', type: 'image/png' },
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          {
            src: 'maskable-icon-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        // Só as escalas do servidor ficam disponíveis offline; nada de convite/confirmação e
        // nenhuma escrita (POST/PUT/DELETE) nunca é cacheada.
        runtimeCaching: [
          {
            urlPattern: ({ url, request }) =>
              request.method === 'GET' && url.pathname === '/api/me/escalas',
            handler: 'NetworkFirst',
            options: {
              cacheName: 'escalas',
              networkTimeoutSeconds: 5,
              cacheableResponse: { statuses: [0, 200] },
            },
          },
        ],
      },
    }),
  ],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/v3/api-docs': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // e2e/ é do Playwright, não do Vitest: nomes como *.spec.ts colidiriam com o padrão daqui.
    exclude: [...configDefaults.exclude, 'e2e/**'],
  },
});
