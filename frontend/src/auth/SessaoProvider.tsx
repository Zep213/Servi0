import { useQueryClient, useQuery } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { aplicarCorDaPastoral, corDaPastoral } from '../tema/pastorais';
import { ContextoSessao, type ValorSessao } from './sessaoContexto';
import {
  lerPastoralSalva,
  papelNaPastoral,
  salvarPastoral,
  type PastoralDoUsuario,
} from './destino';
import { sair as sairDaApi } from './api';
import { usuarioQuery } from './usuarioQuery';

/**
 * Quem está logado (GET /api/me), suas pastorais e a pastoral ativa. A escolha da pastoral fica no
 * localStorage e define a cor do tema.
 */
export function SessaoProvider({ children }: { children: ReactNode }) {
  const cliente = useQueryClient();
  const consulta = useQuery(usuarioQuery);
  const usuario = consulta.data ?? null;

  const pastorais = useMemo<PastoralDoUsuario[]>(
    () =>
      (usuario?.pastorais ?? []).map((p) => ({
        id: p.id as number,
        nome: p.nome as string,
        papel: p.papel as PastoralDoUsuario['papel'],
      })),
    [usuario],
  );

  const [escolhida, definirEscolhida] = useState<number | null>(() => lerPastoralSalva());
  const pastoralAtiva = pastorais.find((p) => p.id === escolhida) ?? pastorais[0] ?? null;

  useEffect(() => {
    if (pastoralAtiva) {
      aplicarCorDaPastoral(corDaPastoral(pastoralAtiva.nome, pastoralAtiva.id));
    }
  }, [pastoralAtiva]);

  const trocarPastoral = useCallback((id: number) => {
    definirEscolhida(id);
    salvarPastoral(id);
  }, []);

  const encerrar = useCallback(() => {
    void sairDaApi().finally(() => {
      cliente.clear();
      window.location.assign('/entrar');
    });
  }, [cliente]);

  const valor: ValorSessao = {
    usuario,
    carregando: consulta.isPending,
    pastorais,
    pastoralAtiva,
    papelAtivo: papelNaPastoral(pastorais, pastoralAtiva?.id ?? null),
    trocarPastoral,
    encerrar,
    recarregar: () => consulta.refetch(),
  };

  return <ContextoSessao.Provider value={valor}>{children}</ContextoSessao.Provider>;
}
