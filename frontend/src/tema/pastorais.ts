import { razaoDeContraste } from './contraste';

/**
 * Cor por pastoral: só no front (o backend não guarda cor). Pastoral conhecida tem cor fixa pelo
 * nome; as demais pegam uma cor da paleta de forma determinística pelo id, então a mesma pastoral
 * tem sempre a mesma cor.
 */

export interface CorPastoral {
  readonly base: string;
  readonly suave: string;
}

const TEXTO_ESCURO = '#111827';
const TEXTO_CLARO = '#ffffff';

/** Cores de fundo de botão e destaque. Todas com texto branco ≥ 4,5:1 (ver testes). */
export const PALETA: readonly CorPastoral[] = [
  { base: '#1d4ed8', suave: '#dbeafe' }, // azul
  { base: '#b91c1c', suave: '#fee2e2' }, // vermelho
  { base: '#047857', suave: '#d1fae5' }, // verde
  { base: '#6d28d9', suave: '#ede9fe' }, // roxo
  { base: '#b45309', suave: '#fef3c7' }, // âmbar escuro
  { base: '#0f766e', suave: '#ccfbf1' }, // teal
  { base: '#92400e', suave: '#fde68a' }, // marrom
  { base: '#be185d', suave: '#fce7f3' }, // rosa
];

/** Cores conhecidas, pelo nome normalizado da pastoral. */
export const CORES_CONHECIDAS: Readonly<Record<string, CorPastoral>> = {
  pascom: PALETA[0] as CorPastoral,
  ecc: PALETA[1] as CorPastoral,
};

/** Minúsculo e sem acento: "Pascom", "PASCOM" e "Páscom" caem na mesma chave. */
export function normalizarNome(nome: string): string {
  return nome.normalize('NFD').replace(/[̀-ͯ]/g, '').trim().toLowerCase();
}

export function corDaPastoral(nome: string, id: number): CorPastoral {
  const conhecida = CORES_CONHECIDAS[normalizarNome(nome)];
  if (conhecida) return conhecida;
  const indice = Math.abs(Math.trunc(id)) % PALETA.length;
  return PALETA[indice] as CorPastoral;
}

/** Texto branco ou quase preto sobre a cor, escolhendo o que tiver mais contraste. */
export function textoSobre(cor: string): string {
  return razaoDeContraste(cor, TEXTO_CLARO) >= razaoDeContraste(cor, TEXTO_ESCURO)
    ? TEXTO_CLARO
    : TEXTO_ESCURO;
}

/** Define --accent e --accent-soft no :root. Chamado ao trocar de pastoral. */
export function aplicarCorDaPastoral(cor: CorPastoral): void {
  const raiz = document.documentElement;
  raiz.style.setProperty('--accent', cor.base);
  raiz.style.setProperty('--accent-soft', cor.suave);
  raiz.style.setProperty('--accent-text', textoSobre(cor.base));
}
