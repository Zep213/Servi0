import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  getGetPastoraisPorPastoralIdReunioesQueryKey,
  getPastoraisPorPastoralIdReunioes,
  postPastoraisPorPastoralIdReunioes,
  postPastoraisPorPastoralIdReunioesSolicitar,
} from '../../api/generated/servio';
import { ErroApi } from '../../api/cliente';
import { useAcessoDaPastoral } from '../../auth/useAcesso';
import { Botao } from '../../componentes/Botao';
import { Campo } from '../../componentes/Campo';
import { Card } from '../../componentes/Card';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Modal } from '../../componentes/Modal';
import { Tabela, type ColunaTabela } from '../../componentes/Tabela';
import { useToast } from '../../componentes/toastContexto';
import type { ReuniaoResponseDTO } from '../../api/generated/modelos';
import { dataPorExtenso, horaCurta } from '../../utilidades/datas';

const esquemaReuniao = z.object({
  titulo: z.string().min(1, 'Dê um título para a reunião').max(120, 'Título muito longo'),
  dataHora: z.string().min(1, 'Informe a data e a hora'),
  local: z.string().max(120, 'Local muito longo').optional(),
});
type DadosReuniao = z.infer<typeof esquemaReuniao>;

/** Reuniões: o coordenador marca; os demais veem e podem pedir uma. */
export function Reunioes() {
  const { pastoralId, acesso } = useAcessoDaPastoral();
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const [pedindo, definirPedindo] = useState(false);
  const [motivo, definirMotivo] = useState('');
  const params = { page: 0, size: 50, sort: ['dataHora'] };
  const consulta = useQuery({
    queryKey: getGetPastoraisPorPastoralIdReunioesQueryKey(pastoralId, params),
    queryFn: async () => (await getPastoraisPorPastoralIdReunioes(pastoralId, params)).data,
  });
  const atualizar = () => {
    void cliente.invalidateQueries({
      queryKey: getGetPastoraisPorPastoralIdReunioesQueryKey(pastoralId),
    });
  };
  const erro = (e: unknown) => {
    mostrar(e instanceof ErroApi ? e.message : 'Não foi possível concluir agora', 'erro');
  };

  const pedir = useMutation({
    mutationFn: () =>
      postPastoraisPorPastoralIdReunioesSolicitar(pastoralId, { motivo: motivo.trim() }),
    onSuccess: () => {
      definirPedindo(false);
      definirMotivo('');
      mostrar('Pedido enviado à coordenação', 'sucesso');
    },
    onError: erro,
  });

  const colunas: ColunaTabela<ReuniaoResponseDTO>[] = [
    {
      chave: 'data',
      titulo: 'Quando',
      celula: (r) =>
        `${dataPorExtenso(r.dataHora?.slice(0, 10))} · ${horaCurta(r.dataHora?.slice(11))}`,
    },
    { chave: 'titulo', titulo: 'Reunião', celula: (r) => r.titulo ?? '' },
    { chave: 'local', titulo: 'Local', celula: (r) => r.local ?? '' },
  ];

  return (
    <>
      <div className="cabecalho-pagina">
        <h1 className="titulo-pagina">Reuniões</h1>
        {acesso.coordena ? (
          <NovaReuniao onCriada={atualizar} onErro={erro} pastoralId={pastoralId} />
        ) : (
          <Botao
            onClick={() => {
              definirPedindo(true);
            }}
          >
            Pedir reunião
          </Botao>
        )}
      </div>
      {consulta.isPending ? <Esqueleto /> : null}
      {consulta.data ? (
        <Card>
          {(consulta.data.content ?? []).length === 0 ? (
            <EstadoVazio titulo="Nenhuma reunião marcada" />
          ) : (
            <Tabela
              titulo="Reuniões"
              linhas={consulta.data.content ?? []}
              chaveDaLinha={(r) => r.id ?? 0}
              colunas={colunas}
            />
          )}
        </Card>
      ) : null}

      <Modal
        titulo="Pedir uma reunião"
        aberto={pedindo}
        onFechar={() => {
          definirPedindo(false);
        }}
      >
        <Campo
          rotulo="Por que você quer uma reunião?"
          value={motivo}
          maxLength={500}
          onChange={(e) => {
            definirMotivo(e.target.value);
          }}
        />
        <Botao
          disabled={motivo.trim() === '' || pedir.isPending}
          onClick={() => {
            pedir.mutate();
          }}
        >
          Enviar pedido
        </Botao>
      </Modal>
    </>
  );
}

function NovaReuniao({
  pastoralId,
  onCriada,
  onErro,
}: {
  pastoralId: number;
  onCriada: () => void;
  onErro: (e: unknown) => void;
}) {
  const [aberto, definirAberto] = useState(false);
  const { mostrar } = useToast();
  const formulario = useForm<DadosReuniao>({
    resolver: zodResolver(esquemaReuniao),
    defaultValues: { titulo: '', dataHora: '', local: '' },
  });
  const marcar = useMutation({
    mutationFn: (dados: DadosReuniao) =>
      postPastoraisPorPastoralIdReunioes(pastoralId, {
        titulo: dados.titulo.trim(),
        dataHora: dados.dataHora,
        local: dados.local?.trim() || undefined,
      }),
    onSuccess: () => {
      mostrar('Reunião marcada', 'sucesso');
      definirAberto(false);
      formulario.reset();
      onCriada();
    },
    onError: onErro,
  });
  const enviar = formulario.handleSubmit((dados) => {
    marcar.mutate(dados);
  });
  return (
    <>
      <Botao
        onClick={() => {
          definirAberto(true);
        }}
      >
        Marcar reunião
      </Botao>
      <Modal
        titulo="Marcar reunião"
        aberto={aberto}
        onFechar={() => {
          definirAberto(false);
        }}
      >
        <form
          onSubmit={(e) => {
            void enviar(e);
          }}
          noValidate
        >
          <Campo
            rotulo="Título"
            erro={formulario.formState.errors.titulo?.message}
            {...formulario.register('titulo')}
          />
          <Campo
            rotulo="Data e hora"
            type="datetime-local"
            erro={formulario.formState.errors.dataHora?.message}
            {...formulario.register('dataHora')}
          />
          <Campo
            rotulo="Local (opcional)"
            erro={formulario.formState.errors.local?.message}
            {...formulario.register('local')}
          />
          <Botao type="submit" disabled={marcar.isPending}>
            Marcar
          </Botao>
        </form>
      </Modal>
    </>
  );
}
