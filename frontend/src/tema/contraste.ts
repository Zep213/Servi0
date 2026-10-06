/**
 * Contraste WCAG entre duas cores em hexadecimal (#rgb ou #rrggbb). Usado para garantir o mínimo
 * de 4,5:1 do texto sobre o fundo, nos tokens de cor e nas cores das pastorais.
 */

export const CONTRASTE_MINIMO_TEXTO = 4.5;

function canal(valor: number): number {
  const c = valor / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

export function converterHex(hex: string): [number, number, number] {
  const limpo = hex.replace('#', '');
  const completo =
    limpo.length === 3
      ? limpo
          .split('')
          .map((c) => c + c)
          .join('')
      : limpo;
  if (!/^[0-9a-fA-F]{6}$/.test(completo)) {
    throw new Error(`Cor inválida: ${hex}`);
  }
  const n = parseInt(completo, 16);
  return [(n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff];
}

export function luminancia(hex: string): number {
  const [r, g, b] = converterHex(hex);
  return 0.2126 * canal(r) + 0.7152 * canal(g) + 0.0722 * canal(b);
}

/** Razão de contraste entre as duas cores, de 1 a 21. */
export function razaoDeContraste(corA: string, corB: string): number {
  const [claro, escuro] = [luminancia(corA), luminancia(corB)].sort((x, y) => y - x) as [
    number,
    number,
  ];
  return (claro + 0.05) / (escuro + 0.05);
}
