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

/** Data de hoje no horário local, em AAAA-MM-DD. */
export function hojeIso(agora: Date = new Date()): string {
  return dataIso(agora);
}

/** Soma dias a uma data AAAA-MM-DD (sem passar pelo fuso). */
export function somarDias(iso: string, dias: number): string {
  const data = dataLocal(iso) ?? new Date();
  data.setDate(data.getDate() + dias);
  return dataIso(data);
}

function dataIso(data: Date): string {
  const ano = String(data.getFullYear());
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${ano}-${mes}-${dia}`;
}

const NOMES_DO_MES = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' });

/** O mês de hoje, em AAAA-MM. Navegação por mês (painel, celebrações, financeiro) parte daqui. */
export function mesAtual(): string {
  return hojeIso().slice(0, 7);
}

/** Desloca um mês AAAA-MM pela quantidade de meses (negativa para o anterior). */
export function deslocarMes(mes: string, quantidade: number): string {
  const [ano, m] = mes.split('-').map(Number) as [number, number];
  const data = new Date(ano, m - 1 + quantidade, 1);
  return `${String(data.getFullYear())}-${String(data.getMonth() + 1).padStart(2, '0')}`;
}

/** "2026-10" → "outubro de 2026". */
export function nomeDoMes(mes: string): string {
  const [ano, m] = mes.split('-').map(Number) as [number, number];
  return NOMES_DO_MES.format(new Date(ano, m - 1, 1));
}

/** Primeiro e último dia (AAAA-MM-DD) de um mês AAAA-MM, para filtrar por período. */
export function limitesDoMes(mes: string): { de: string; ate: string } {
  const [ano, m] = mes.split('-').map(Number) as [number, number];
  return {
    de: `${mes}-01`,
    ate: dataIso(new Date(ano, m, 0)),
  };
}
