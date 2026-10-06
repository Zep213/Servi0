/**
 * Datas e horários em português, para a pessoa ler. A API manda data como AAAA-MM-DD, hora como
 * HH:mm:ss e prazo como AAAA-MM-DDTHH:mm:ss (sem fuso). Tudo é lido como horário local, sem
 * conversão, porque a aplicação inteira usa o fuso da paróquia.
 */

const FORMATO_EXTENSO = new Intl.DateTimeFormat('pt-BR', {
  weekday: 'long',
  day: 'numeric',
  month: 'long',
  year: 'numeric',
});

function dataLocal(iso: string): Date | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso);
  if (!partes) return null;
  const [, ano, mes, dia] = partes;
  return new Date(Number(ano), Number(mes) - 1, Number(dia));
}

/** "2031-03-02" → "domingo, 2 de março de 2031". Texto desconhecido volta como veio. */
export function dataPorExtenso(iso: string | undefined): string {
  if (!iso) return '';
  const data = dataLocal(iso);
  return data ? FORMATO_EXTENSO.format(data) : iso;
}

/** "18:30:00" → "18:30". */
export function horaCurta(hora: string | undefined): string {
  if (!hora) return '';
  return hora.slice(0, 5);
}

const MESES = [
  'janeiro',
  'fevereiro',
  'março',
  'abril',
  'maio',
  'junho',
  'julho',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'dezembro',
];

/** "2026-10-08T18:00:00" → "8 de outubro às 18:00". */
export function prazoLegivel(prazo: string | undefined): string {
  if (!prazo) return '';
  const partes = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(prazo);
  if (!partes) return prazo;
  const [, , mes, dia, hh, mm] = partes;
  return `${String(Number(dia))} de ${MESES[Number(mes) - 1] ?? ''} às ${hh ?? ''}:${mm ?? ''}`;
}

/** Primeiro nome, para a saudação. */
export function primeiroNome(nome: string | undefined): string {
  return (nome ?? '').trim().split(/\s+/)[0] ?? '';
}
