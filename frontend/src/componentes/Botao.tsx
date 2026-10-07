import type { ButtonHTMLAttributes } from 'react';

export type VarianteBotao = 'primario' | 'secundario' | 'perigo';

interface BotaoProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variante?: VarianteBotao;
}

/** Alvo de toque de pelo menos 44px. O primário usa a cor da pastoral (--accent). */
export function Botao({ variante = 'primario', className, type = 'button', ...resto }: BotaoProps) {
  const classes = ['botao', `botao--${variante}`, className].filter(Boolean).join(' ');
  return <button type={type} className={classes} {...resto} />;
}
