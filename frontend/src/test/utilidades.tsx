import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router';
import type { ReactNode } from 'react';
import { SessaoProvider } from '../auth/SessaoProvider';
import { rotas } from '../rotas';
import { ProvedorToast } from '../componentes/Toast';

/** Cliente novo a cada teste: nada do cache de um teste vaza para o outro. */
export function novoCliente(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
}

/** Renderiza as rotas reais numa memória, a partir de um caminho. */
export function renderizarRotas(caminho: string, mapa: RouteObject[] = rotas) {
  const cliente = novoCliente();
  const roteador = createMemoryRouter(mapa, { initialEntries: [caminho] });
  const tela = render(
    <QueryClientProvider client={cliente}>
      <SessaoProvider>
        <ProvedorToast>
          <RouterProvider router={roteador} />
        </ProvedorToast>
      </SessaoProvider>
    </QueryClientProvider>,
  );
  return { ...tela, roteador, cliente };
}

export function comProvedores(filho: ReactNode) {
  return (
    <QueryClientProvider client={novoCliente()}>
      <SessaoProvider>{filho}</SessaoProvider>
    </QueryClientProvider>
  );
}
