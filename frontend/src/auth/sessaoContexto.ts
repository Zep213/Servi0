import { createContext, useContext } from 'react';
import type { MeResponseDTO } from '../api/generated/modelos';
import type { PapelPastoral, PastoralDoUsuario } from './destino';

export interface ValorSessao {
  usuario: MeResponseDTO | null;
  carregando: boolean;
  pastorais: readonly PastoralDoUsuario[];
  pastoralAtiva: PastoralDoUsuario | null;
  papelAtivo: PapelPastoral | null;
  trocarPastoral: (id: number) => void;
  encerrar: () => void;
  recarregar: () => Promise<unknown>;
}

export const ContextoSessao = createContext<ValorSessao | null>(null);

export function useSessao(): ValorSessao {
  const valor = useContext(ContextoSessao);
  if (!valor) throw new Error('useSessao precisa de SessaoProvider');
  return valor;
}
