import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

/**
 * Servidor de API simulado nos testes. O CSRF é padrão: toda escrita busca o cookie antes.
 * Cada teste declara as demais respostas que precisa.
 */
export const servidor = setupServer(
  http.get('/api/auth/csrf', () => new HttpResponse(null, { status: 204 })),
);
