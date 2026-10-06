import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router';
import { queryClient } from './api/queryClient';
import { SessaoProvider } from './auth/SessaoProvider';
import { ProvedorToast } from './componentes/Toast';
import { roteador } from './rotas';

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SessaoProvider>
        <ProvedorToast>
          <RouterProvider router={roteador} />
        </ProvedorToast>
      </SessaoProvider>
    </QueryClientProvider>
  );
}
