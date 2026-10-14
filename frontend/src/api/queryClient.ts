import { QueryClient } from '@tanstack/react-query';
import { ErroApi, definirAoSessaoExpirada } from './cliente';

/** Erros 4xx (exceto 429) não são repetidos: repetir não muda a resposta. */
function deveRepetir(tentativas: number, erro: unknown): boolean {
  if (erro instanceof ErroApi && erro.status < 500 && erro.status !== 429) return false;
  return tentativas < 2;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: deveRepetir },
    mutations: { retry: false },
  },
});

// Sessão caiu: apaga o cache (dados de outra pessoa não podem ficar na tela) e volta ao login.
definirAoSessaoExpirada(() => {
  queryClient.clear();
  if (window.location.pathname !== '/entrar') {
    window.location.assign('/entrar');
  }
});
