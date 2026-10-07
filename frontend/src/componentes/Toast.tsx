import { useCallback, useMemo, useState, type ReactNode } from 'react';
import { Contexto, type Aviso, type TipoToast } from './toastContexto';

/** Avisos curtos que somem sozinhos; a região é anunciada por leitor de tela. */
export function ProvedorToast({ children }: { children: ReactNode }) {
  const [avisos, definirAvisos] = useState<Aviso[]>([]);

  const mostrar = useCallback((texto: string, tipo: TipoToast = 'info') => {
    const id = Date.now() + Math.random();
    definirAvisos((atuais) => [...atuais, { id, texto, tipo }]);
    setTimeout(() => {
      definirAvisos((atuais) => atuais.filter((a) => a.id !== id));
    }, 4000);
  }, []);

  const valor = useMemo(() => ({ mostrar }), [mostrar]);

  return (
    <Contexto.Provider value={valor}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {avisos.map((a) => (
          <p key={a.id} className={`toast toast--${a.tipo}`}>
            {a.texto}
          </p>
        ))}
      </div>
    </Contexto.Provider>
  );
}
