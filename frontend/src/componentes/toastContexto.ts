import { createContext, useContext } from 'react';

export type TipoToast = 'sucesso' | 'erro' | 'info';

export interface Aviso {
  id: number;
  texto: string;
  tipo: TipoToast;
}

export interface ContextoToast {
  mostrar: (texto: string, tipo?: TipoToast) => void;
}

export const Contexto = createContext<ContextoToast | null>(null);

export function useToast(): ContextoToast {
  const ctx = useContext(Contexto);
  if (!ctx) throw new Error('useToast precisa de ProvedorToast');
  return ctx;
}
