import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate, useParams } from 'react-router';
import type { z } from 'zod';
import {
  deleteCelebracoesPorId,
  getCelebracoesPorId,
  getComunidades,
  getFuncoes,
  getGetCelebracoesPorIdQueryKey,
  getGetCelebracoesQueryKey,
  getGetComunidadesQueryKey,
  getGetFuncoesQueryKey,
  getGetPastoraisQueryKey,
  getPastorais,
  postVagas,
  putCelebracoesPorId,
} from '../../api/generated/servio';
import { ErroApi } from '../../api/cliente';
import { Botao } from '../../componentes/Botao';
import { Card } from '../../componentes/Card';
import { DialogoConfirmacao } from '../../componentes/DialogoConfirmacao';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Modal } from '../../componentes/Modal';
import { useToast } from '../../componentes/toastContexto';
import { dataPorExtenso, horaCurta } from '../../utilidades/datas';
import { CamposCelebracao } from './CamposCelebracao';
import {
  esquemaCelebracao,
  NOME_DO_TIPO,
  NOME_DO_TIPO_DATA,
  type DadosCelebracao,
} from './celebracaoCampos';

function mensagem(erro: unknown, padrao: string): string {
  return erro instanceof ErroApi ? erro.message : padrao;
}

/** Uma celebração: dados, editar/excluir e responsabilizar uma pastoral por ela (só padre/admin). */
export function CelebracaoDetalhe() {
  const { celebracaoId } = useParams();
  const id = Number(celebracaoId);
  const navegar = useNavigate();
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const [editando, definirEditando] = useState(false);
  const [excluindo, definirExcluindo] = useState(false);

  const celebracao = useQuery({
    queryKey: getGetCelebracoesPorIdQueryKey(id),
    queryFn: async () => (await getCelebracoesPorId(id)).data,
  });
  const comunidades = useQuery({
    queryKey: getGetComunidadesQueryKey({ size: 100 }),
    queryFn: async () => (await getComunidades({ size: 100 })).data,
  });
  const listaDeComunidades = comunidades.data?.content ?? [];
  const nomeDaComunidade = listaDeComunidades.find((c) => c.id === celebracao.data?.comunidadeId)
    ?.nome;

  const editar = useMutation({
    mutationFn: (dados: DadosCelebracao) =>
      putCelebracoesPorId(id, {
        comunidadeId: dados.comunidadeId,
        data: dados.data,
        hora: dados.hora,
        tipoData: dados.tipoData,
        tipo: dados.tipo,
        titulo: dados.titulo?.trim() || undefined,
      }),
    onSuccess: () => {
      mostrar('Celebração atualizada', 'sucesso');
      definirEditando(false);
      void cliente.invalidateQueries({ queryKey: getGetCelebracoesPorIdQueryKey(id) });
      void cliente.invalidateQueries({ queryKey: getGetCelebracoesQueryKey() });
    },
    onError: (e: unknown) => {
      mostrar(mensagem(e, 'Não foi possível salvar agora'), 'erro');
    },
  });

  const excluir = useMutation({
    mutationFn: () => deleteCelebracoesPorId(id),
    onSuccess: () => {
      mostrar('Celebração excluída', 'sucesso');
      void cliente.invalidateQueries({ queryKey: getGetCelebracoesQueryKey() });
      void navegar('/celebracoes');
    },
    onError: (e: unknown) => {
      mostrar(mensagem(e, 'Não foi possível excluir agora'), 'erro');
    },
    onSettled: () => {
      definirExcluindo(false);
    },
  });

  if (celebracao.isPending) return <Esqueleto linhas={6} />;
  if (celebracao.isError) {
    return (
      <EstadoVazio
        titulo="Não foi possível carregar esta celebração"
        texto="Verifique sua conexão e atualize a página."
      />
    );
  }
  const dados = celebracao.data;

  return (
    <>
      <div className="cabecalho-pagina">
        <h1 className="titulo-pagina">{dados.titulo || NOME_DO_TIPO[dados.tipo ?? ''] || 'Celebração'}</h1>
        <div className="acoes-linha">
          <Botao
            variante="secundario"
            onClick={() => {
              definirEditando(true);
            }}
          >
            Editar
          </Botao>
          <Botao
            variante="perigo"
            onClick={() => {
              definirExcluindo(true);
            }}
          >
            Excluir
          </Botao>
        </div>
      </div>
      <Card>
        <dl className="convite__dados">
          <dt>Data</dt>
          <dd>
            {dataPorExtenso(dados.data)} · {horaCurta(dados.hora)}
          </dd>
          <dt>Comunidade</dt>
          <dd>{nomeDaComunidade ?? ''}</dd>
          <dt>Tipo</dt>
          <dd>{NOME_DO_TIPO[dados.tipo ?? ''] ?? ''}</dd>
          <dt>Tipo de data</dt>
          <dd>{NOME_DO_TIPO_DATA[dados.tipoData ?? ''] ?? ''}</dd>
        </dl>
      </Card>

      <ResponsabilizarPastoral celebracaoId={id} />

      {editando ? (
        <Modal
          titulo="Editar celebração"
          aberto
          onFechar={() => {
            definirEditando(false);
          }}
        >
          <FormularioEdicao
            comunidades={listaDeComunidades}
            valoresIniciais={{
              comunidadeId: dados.comunidadeId ?? 0,
              data: dados.data ?? '',
              hora: dados.hora ?? '',
              tipoData: dados.tipoData ?? 'NORMAL',
              tipo: dados.tipo ?? 'MISSA_DOMINICAL',
              titulo: dados.titulo ?? '',
            }}
            enviando={editar.isPending}
            onEnviar={(valores) => {
              editar.mutate(valores);
            }}
          />
        </Modal>
      ) : null}

      <DialogoConfirmacao
        aberto={excluindo}
        titulo="Excluir esta celebração?"
        mensagem="As vagas e escalas já criadas para ela deixam de valer."
        textoConfirmar="Excluir"
        perigoso
        onCancelar={() => {
          definirExcluindo(false);
        }}
        onConfirmar={() => {
          excluir.mutate();
        }}
      />
    </>
  );
}

function FormularioEdicao({
  comunidades,
  valoresIniciais,
  enviando,
  onEnviar,
}: {
  comunidades: { id?: number; nome?: string }[];
  valoresIniciais: DadosCelebracao;
  enviando: boolean;
  onEnviar: (dados: DadosCelebracao) => void;
}) {
  const formulario = useForm<z.input<typeof esquemaCelebracao>, unknown, DadosCelebracao>({
    resolver: zodResolver(esquemaCelebracao),
    defaultValues: valoresIniciais,
  });
  const enviar = formulario.handleSubmit((dados) => {
    onEnviar(dados);
  });
  return (
    <form
      onSubmit={(e) => {
        void enviar(e);
      }}
      noValidate
    >
      <CamposCelebracao formulario={formulario} comunidades={comunidades} />
      <Botao type="submit" disabled={enviando}>
        Salvar
      </Botao>
    </form>
  );
}

/** Escolhe a pastoral e a função; cria a vaga sem quantidade (quem define depois é a coordenação). */
function ResponsabilizarPastoral({ celebracaoId }: { celebracaoId: number }) {
  const { mostrar } = useToast();
  const [pastoralId, definirPastoralId] = useState<number | ''>('');
  const [funcaoId, definirFuncaoId] = useState<number | ''>('');

  const pastorais = useQuery({
    queryKey: getGetPastoraisQueryKey({ size: 100 }),
    queryFn: async () => (await getPastorais({ size: 100 })).data,
  });
  const funcoes = useQuery({
    queryKey: getGetFuncoesQueryKey({ pastoralId: pastoralId === '' ? undefined : pastoralId }),
    queryFn: async () =>
      (await getFuncoes({ pastoralId: pastoralId === '' ? undefined : pastoralId, size: 100 }))
        .data,
    enabled: pastoralId !== '',
  });

  const responsabilizar = useMutation({
    mutationFn: () => postVagas({ celebracaoId, funcaoId: funcaoId as number }),
    onSuccess: () => {
      mostrar(
        'Pastoral responsabilizada. A coordenação dela já pode definir a quantidade e escalar.',
        'sucesso',
      );
      definirFuncaoId('');
    },
    onError: (e: unknown) => {
      mostrar(mensagem(e, 'Não foi possível responsabilizar a pastoral agora'), 'erro');
    },
  });

  return (
    <Card titulo="Responsabilizar pastoral">
      <p className="texto-corpo">
        Escolha a pastoral e a função. A quantidade de pessoas é definida depois, pela coordenação
        da pastoral, na escala da celebração.
      </p>
      <label className="campo">
        <span className="campo__rotulo">Pastoral</span>
        <select
          value={pastoralId}
          onChange={(e) => {
            const valor = e.target.value;
            definirPastoralId(valor === '' ? '' : Number(valor));
            definirFuncaoId('');
          }}
        >
          <option value="">Escolha a pastoral</option>
          {(pastorais.data?.content ?? []).map((p) => (
            <option key={p.id} value={p.id}>
              {p.nome}
            </option>
          ))}
        </select>
      </label>
      <label className="campo">
        <span className="campo__rotulo">Função</span>
        <select
          value={funcaoId}
          disabled={pastoralId === ''}
          onChange={(e) => {
            const valor = e.target.value;
            definirFuncaoId(valor === '' ? '' : Number(valor));
          }}
        >
          <option value="">Escolha a função</option>
          {(funcoes.data?.content ?? []).map((f) => (
            <option key={f.id} value={f.id}>
              {f.nome}
            </option>
          ))}
        </select>
      </label>
      <Botao
        disabled={funcaoId === '' || responsabilizar.isPending}
        onClick={() => {
          responsabilizar.mutate();
        }}
      >
        Responsabilizar
      </Botao>
    </Card>
  );
}
