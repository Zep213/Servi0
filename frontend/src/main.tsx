import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { garantirCsrf } from './api/cliente';
import { App } from './App';

// Ao abrir o app, pega o cookie XSRF-TOKEN uma vez; as escritas dependem dele.
void garantirCsrf().catch(() => {
  // sem conexão: a tela de entrada mostra o erro quando a pessoa tentar
});

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
