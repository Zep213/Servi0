import type { UseFormReturn } from 'react-hook-form';
import type { z } from 'zod';
import type { ComunidadeResponseDTO } from '../../api/generated/modelos';
import { Campo } from '../../componentes/Campo';
import {
  esquemaCelebracao,
  NOME_DO_TIPO,
  NOME_DO_TIPO_DATA,
  type DadosCelebracao,
} from './celebracaoCampos';

/** Campos do formulário de celebração (comunidade, data, hora, tipo, título), para criar e editar. */
export function CamposCelebracao({
  formulario,
  comunidades,
}: {
  formulario: UseFormReturn<z.input<typeof esquemaCelebracao>, unknown, DadosCelebracao>;
  comunidades: ComunidadeResponseDTO[];
}) {
  const { register, formState } = formulario;
  const erros = formState.errors;
  return (
    <>
      <label className="campo">
        <span className="campo__rotulo">Comunidade</span>
        <select {...register('comunidadeId')}>
          <option value={0} disabled>
            Escolha a comunidade
          </option>
          {comunidades.map((c) => (
            <option key={c.id} value={c.id}>
              {c.nome}
            </option>
          ))}
        </select>
      </label>
      {erros.comunidadeId ? <p className="campo__erro">{erros.comunidadeId.message}</p> : null}
      <Campo rotulo="Data" type="date" erro={erros.data?.message} {...register('data')} />
      <Campo rotulo="Hora" type="time" erro={erros.hora?.message} {...register('hora')} />
      <label className="campo">
        <span className="campo__rotulo">Tipo</span>
        <select {...register('tipo')}>
          {Object.entries(NOME_DO_TIPO).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>
              {rotulo}
            </option>
          ))}
        </select>
      </label>
      <label className="campo">
        <span className="campo__rotulo">Tipo de data</span>
        <select {...register('tipoData')}>
          {Object.entries(NOME_DO_TIPO_DATA).map(([valor, rotulo]) => (
            <option key={valor} value={valor}>
              {rotulo}
            </option>
          ))}
        </select>
      </label>
      <Campo rotulo="Título (opcional)" erro={erros.titulo?.message} {...register('titulo')} />
    </>
  );
}
