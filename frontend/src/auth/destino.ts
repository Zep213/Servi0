import type { MeResponseDTO } from '../api/generated/modelos';

export type PapelPastoral = 'COORDENADOR' | 'VICE' | 'SECRETARIO' | 'TESOUREIRO' | 'MEMBRO';

/** Papéis que enxergam a gestão da pastoral (o painel e o que vem dele). */
export const PAPEIS_DE_GESTAO: readonly PapelPastoral[] = [
  'COORDENADOR',
  'VICE',
  'SECRETARIO',
  'TESOUREIRO',
];

export interface PastoralDoUsuario {
  id: number;
  nome: string;
  papel: PapelPastoral;
}

/**
 * Para onde a pessoa vai ao entrar. MEMBRO vai às suas escalas; quem tem papel de gestão vai ao
 * painel da pastoral ativa; PADRE e ADMIN sem papel vão às celebrações.
 */
export function destinoInicial(
  usuario: Pick<MeResponseDTO, 'perfil'>,
  pastorais: readonly PastoralDoUsuario[],
  pastoralAtiva: number | null,
): string {
  const ativa = pastorais.find((p) => p.id === pastoralAtiva) ?? pastorais[0];
  if (ativa && PAPEIS_DE_GESTAO.includes(ativa.papel)) {
    return `/pastoral/${String(ativa.id)}/painel`;
  }
  if (usuario.perfil === 'PADRE' || usuario.perfil === 'ADMIN') {
    return '/celebracoes';
  }
  return '/minhas-escalas';
}

/** Papel da pessoa na pastoral ativa, ou null se não participa dela. */
export function papelNaPastoral(
  pastorais: readonly PastoralDoUsuario[],
  pastoralId: number | null,
): PapelPastoral | null {
  return pastorais.find((p) => p.id === pastoralId)?.papel ?? null;
}

const CHAVE_PASTORAL = 'servio.pastoralAtiva';

/** Lê a pastoral escolhida. localStorage pode falhar (modo privado, bloqueio): nesse caso, sem memória. */
export function lerPastoralSalva(): number | null {
  try {
    const valor = window.localStorage.getItem(CHAVE_PASTORAL);
    const numero = valor === null ? NaN : Number(valor);
    return Number.isInteger(numero) ? numero : null;
  } catch {
    return null;
  }
}

export function salvarPastoral(id: number): void {
  try {
    window.localStorage.setItem(CHAVE_PASTORAL, String(id));
  } catch {
    // sem persistência: a escolha vale só nesta aba
  }
}
