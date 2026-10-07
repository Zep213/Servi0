import { useEffect, useState } from 'react';

/** Se o navegador diz que está online agora. Atualiza com os eventos online/offline da janela. */
export function useConexao(): boolean {
  const [online, definirOnline] = useState(() => navigator.onLine);
  useEffect(() => {
    const aoMudar = () => {
      definirOnline(navigator.onLine);
    };
    window.addEventListener('online', aoMudar);
    window.addEventListener('offline', aoMudar);
    return () => {
      window.removeEventListener('online', aoMudar);
      window.removeEventListener('offline', aoMudar);
    };
  }, []);
  return online;
}
