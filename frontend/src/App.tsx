import { QueryClientProvider } from '@tanstack/react-query';
import { queryClient } from './api/queryClient';

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <main>Servio</main>
    </QueryClientProvider>
  );
}
