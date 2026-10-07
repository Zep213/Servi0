import type { ReactNode } from 'react';

/** Ícones em SVG (nunca emoji). Traço de 2px, herdam a cor do texto. */
const CAMINHOS: Record<NomeIcone, ReactNode> = {
  painel: <path d="M4 4h7v7H4zM13 4h7v4h-7zM13 10h7v10h-7zM4 13h7v7H4z" />,
  calendario: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="2" />
      <path d="M3 10h18M8 3v4M16 3v4" />
    </>
  ),
  pessoas: (
    <>
      <circle cx="9" cy="8" r="3.5" />
      <path d="M2.5 20c.6-3.4 3.2-5.5 6.5-5.5s5.9 2.1 6.5 5.5M16 4.5a3.5 3.5 0 0 1 0 7M18 14.8c1.8.9 3 2.9 3.3 5.2" />
    </>
  ),
  financeiro: (
    <>
      <path d="M4 19V9M10 19V5M16 19v-7M22 19H2" />
    </>
  ),
  reuniao: (
    <>
      <path d="M4 5h16v11H9l-5 4z" />
    </>
  ),
  configuracoes: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1" />
    </>
  ),
  sair: <path d="M9 4H5v16h4M14 8l4 4-4 4M18 12H9" />,
  check: <path d="M4 12.5 9.5 18 20 6.5" />,
  fechar: <path d="M6 6l12 12M18 6 6 18" />,
  alerta: (
    <>
      <path d="M12 3 2 20h20z" />
      <path d="M12 10v4M12 17.5v.5" />
    </>
  ),
  relogio: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  local: (
    <>
      <path d="M12 21s-6-5.6-6-10.5a6 6 0 0 1 12 0C18 15.4 12 21 12 21z" />
      <circle cx="12" cy="10.5" r="2.2" />
    </>
  ),
};

export type NomeIcone =
  | 'painel'
  | 'calendario'
  | 'pessoas'
  | 'financeiro'
  | 'reuniao'
  | 'configuracoes'
  | 'sair'
  | 'check'
  | 'fechar'
  | 'alerta'
  | 'relogio'
  | 'local';

interface IconeProps {
  nome: NomeIcone;
  titulo?: string;
  tamanho?: number;
}

export function Icone({ nome, titulo, tamanho = 22 }: IconeProps) {
  return (
    <svg
      width={tamanho}
      height={tamanho}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden={titulo ? undefined : true}
      role={titulo ? 'img' : undefined}
    >
      {titulo ? <title>{titulo}</title> : null}
      {CAMINHOS[nome]}
    </svg>
  );
}
