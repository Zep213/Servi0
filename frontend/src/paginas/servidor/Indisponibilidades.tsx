import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  deleteIndisponibilidadesPorId,
  getGetIndisponibilidadesQueryKey,
  getIndisponibilidades,
  postIndisponibilidades,
} from '../../api/generated/servio';
import type { IndisponibilidadeResponseDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { useSessao } from '../../auth/sessaoContexto';
import { Botao } from '../../componentes/Botao';
import { Campo } from '../../componentes/Campo';
import { Card } from '../../componentes/Card';
import { DialogoConfirmacao } from '../../componentes/DialogoConfirmacao';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Tabela, type ColunaTabela } from '../../componentes/Tabela';
import { useToast } from '../../componentes/toastContexto';
import { dataPorExtenso } from '../../utilidades/datas';

const esquema = z
  .object({
    dataInicio: z.string().min(1, 'Informe a data inicial'),
    dataFim: z.string().min(1, 'Informe a data final'),
    motivo: z.string().max(500, 'O motivo pode ter no máximo 500 caracteres').optional(),
  })
  .refine((d) => !d.dataInicio || !d.dataFim || d.dataFim >= d.dataInicio, {
    path: ['dataFim'],
    message: 'A data final não pode ser antes da inicial',
  });
type Dados = z.infer<typeof esquema>;

/** Minhas indisponibilidades: a pessoa marca os dias em que não pode servir e pode apagar. */
export function Indisponibilidades() {
  const { usuario } = useSessao();
  const meuId = usuario?.id ?? null;
  const consulta = useQuery({
    queryKey: getGetIndisponibilidadesQueryKey({ page: 0, size: 100, sort: ['dataInicio'] }),
    queryFn: async () =>
      (await getIndisponibilidades({ page: 0, size: 100, sort: ['dataInicio'] })).data,
  });
  const minhas = (consulta.data?.content ?? []).filter((i) => i.usuarioId === meuId);
  const [paraApagar, definirParaApagar] = useState<IndisponibilidadeResponseDTO | null>(null);

  return (
    <>
      <h1 className="titulo-pagina">Minhas indisponibilidades</h1>
      <p className="texto-corpo">
        Marque os dias em que você não pode servir. A coordenação vê ao escalar.
      </p>
      <FormularioNova />
      <Card titulo="Marcadas">
        {consulta.isPending ? <Esqueleto /> : null}
        {consulta.isError ? (
          <EstadoVazio
            titulo="Não foi possível carregar"
            texto="Verifique sua conexão e atualize a página."
          />
        ) : null}
        {consulta.data && minhas.length === 0 ? (
          <EstadoVazio titulo="Nenhuma indisponibilidade marcada" />
        ) : null}
        {minhas.length > 0 ? (
          <Tabela
            titulo="Minhas indisponibilidades"
            linhas={minhas}
            chaveDaLinha={(i) => i.id ?? 0}
            colunas={colunasIndisponibilidade(definirParaApagar)}
          />
        ) : null}
      </Card>
      <ConfirmarRemocao
        alvo={paraApagar}
        onFechar={() => {
          definirParaApagar(null);
        }}
      />
    </>
  );
}

function colunasIndisponibilidade(
  pedirRemocao: (i: IndisponibilidadeResponseDTO) => void,
): ColunaTabela<IndisponibilidadeResponseDTO>[] {
  return [
    {
      chave: 'periodo',
      titulo: 'Período',
      celula: (i) => `${dataPorExtenso(i.dataInicio)} até ${dataPorExtenso(i.dataFim)}`,
    },
    { chave: 'motivo', titulo: 'Motivo', celula: (i) => i.motivo ?? '' },
    {
      chave: 'acao',
      titulo: '',
      celula: (i) => (
        <Botao
          variante="secundario"
          onClick={() => {
            pedirRemocao(i);
          }}
        >
          Apagar
        </Botao>
      ),
    },
  ];
}

function FormularioNova() {
  const { usuario } = useSessao();
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const formulario = useForm<Dados>({
    resolver: zodResolver(esquema),
    defaultValues: { dataInicio: '', dataFim: '', motivo: '' },
  });

  const criar = useMutation({
    mutationFn: (dados: Dados) => {
      return postIndisponibilidades({
        usuarioId: usuario?.id,
        dataInicio: dados.dataInicio,
        dataFim: dados.dataFim,
        motivo: dados.motivo?.trim() || undefined,
      });
    },
    onSuccess: () => {
      formulario.reset({ dataInicio: '', dataFim: '', motivo: '' });
      mostrar('Indisponibilidade marcada', 'sucesso');
      void cliente.invalidateQueries({ queryKey: getGetIndisponibilidadesQueryKey() });
    },
    onError: (erro: unknown) => {
      if (erro instanceof ErroApi && Object.keys(erro.erros).length > 0) {
        for (const [campo, mensagem] of Object.entries(erro.erros)) {
          if (campo === 'dataInicio' || campo === 'dataFim' || campo === 'motivo') {
            formulario.setError(campo, { message: mensagem });
          }
        }
        return;
      }
      mostrar(erro instanceof ErroApi ? erro.message : 'Não foi possível salvar agora', 'erro');
    },
  });

  const enviar = formulario.handleSubmit((dados) => {
    criar.mutate(dados);
  });

  return (
    <Card titulo="Marcar um período">
      <form
        onSubmit={(e) => {
          void enviar(e);
        }}
        noValidate
      >
        <Campo
          rotulo="Data inicial"
          type="date"
          erro={formulario.formState.errors.dataInicio?.message}
          {...formulario.register('dataInicio')}
        />
        <Campo
          rotulo="Data final"
          type="date"
          erro={formulario.formState.errors.dataFim?.message}
          {...formulario.register('dataFim')}
        />
        <Campo
          rotulo="Motivo (opcional)"
          erro={formulario.formState.errors.motivo?.message}
          {...formulario.register('motivo')}
        />
        <Botao type="submit" disabled={criar.isPending}>
          Marcar período
        </Botao>
      </form>
    </Card>
  );
}

function ConfirmarRemocao({
  alvo,
  onFechar,
}: {
  alvo: IndisponibilidadeResponseDTO | null;
  onFechar: () => void;
}) {
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const remover = useMutation({
    mutationFn: (id: number) => deleteIndisponibilidadesPorId(id),
    onSuccess: () => {
      mostrar('Indisponibilidade apagada', 'sucesso');
      void cliente.invalidateQueries({ queryKey: getGetIndisponibilidadesQueryKey() });
    },
    onError: (erro: unknown) => {
      mostrar(erro instanceof ErroApi ? erro.message : 'Não foi possível apagar agora', 'erro');
    },
    onSettled: onFechar,
  });
  return (
    <DialogoConfirmacao
      aberto={alvo !== null}
      titulo="Apagar esta indisponibilidade?"
      mensagem={
        alvo
          ? `Você voltará a aparecer como disponível de ${dataPorExtenso(alvo.dataInicio)} até ${dataPorExtenso(alvo.dataFim)}.`
          : ''
      }
      textoConfirmar="Apagar"
      perigoso
      onCancelar={onFechar}
      onConfirmar={() => {
        if (alvo?.id !== undefined) remover.mutate(alvo.id);
      }}
    />
  );
}
