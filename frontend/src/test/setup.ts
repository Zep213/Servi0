import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { toHaveNoViolations } from 'jest-axe';
import { afterAll, afterEach, beforeAll, expect } from 'vitest';
import { servidor } from './servidor';

expect.extend(toHaveNoViolations);

// No Node, fetch não aceita caminho relativo: os testes resolvem /api/... na origem da página,
// como o navegador faz.
const fetchOriginal = globalThis.fetch;
globalThis.fetch = (entrada: RequestInfo | URL, opcoes?: RequestInit) => {
  if (typeof entrada === 'string' && entrada.startsWith('/')) {
    return fetchOriginal(new URL(entrada, window.location.origin).href, opcoes);
  }
  return fetchOriginal(entrada, opcoes);
};

// O jsdom não implementa rolagem; a navegação inferior chama scrollIntoView.
Element.prototype.scrollIntoView = () => undefined;

beforeAll(() => {
  servidor.listen();
});

afterEach(() => {
  cleanup();
  servidor.resetHandlers();
  window.localStorage.clear();
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
});

afterAll(() => {
  servidor.close();
});
