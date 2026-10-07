import { z } from 'zod';

export const NOME_DO_TIPO: Record<string, string> = {
  MISSA_DOMINICAL: 'Missa dominical',
  EVENTO: 'Evento',
};

export const NOME_DO_TIPO_DATA: Record<string, string> = {
  NORMAL: 'Normal',
  SOLENIDADE: 'Solenidade',
  FESTA: 'Festa',
  FERIADO: 'Feriado',
};

export const esquemaCelebracao = z.object({
  comunidadeId: z.coerce
    .number({ message: 'Escolha a comunidade' })
    .int()
    .positive('Escolha a comunidade'),
  data: z.string().min(1, 'Informe a data'),
  hora: z.string().min(1, 'Informe a hora'),
  tipoData: z.enum(['NORMAL', 'SOLENIDADE', 'FESTA', 'FERIADO']),
  tipo: z.enum(['MISSA_DOMINICAL', 'EVENTO']),
  titulo: z
    .string()
    .max(120, 'O título pode ter no máximo 120 caracteres')
    .optional()
    .or(z.literal('')),
});

export type DadosCelebracao = z.infer<typeof esquemaCelebracao>;

export const VALORES_PADRAO_CELEBRACAO: DadosCelebracao = {
  comunidadeId: 0,
  data: '',
  hora: '',
  tipoData: 'NORMAL',
  tipo: 'MISSA_DOMINICAL',
  titulo: '',
};
