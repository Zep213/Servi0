export type StatusSelo = 'pendente' | 'confirmado' | 'recusado' | 'expirado' | 'neutro' | 'atencao';

const TEXTOS: Record<StatusSelo, string> = {
  pendente: 'Aguardando resposta',
  confirmado: 'Confirmado',
  recusado: 'Recusado',
  expirado: 'Prazo encerrado',
  neutro: 'Sem convite',
  atencao: 'Atenção',
};

/** O texto sempre aparece: a cor não é o único jeito de dizer o status. */
export function SeloStatus({ status, texto }: { status: StatusSelo; texto?: string }) {
  return <span className={`selo selo--${status}`}>{texto ?? TEXTOS[status]}</span>;
}
