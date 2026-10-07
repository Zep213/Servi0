import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  deleteUsuariosPastoraisPorId,
  getGetPastoraisPorPastoralIdMembrosQueryKey,
  getPastoraisPorPastoralIdMembros,
  getUsuariosBusca,
  postUsuariosPastorais,
  putUsuariosPastoraisPorId,
} from '../../api/generated/servio';
import type { MembroPastoralDTO, ReferenciaDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { useAcessoDaPastoral } from '../../auth/useAcesso';
import { Botao } from '../../componentes/Botao';
import { Campo } from '../../componentes/Campo';
import { Card } from '../../componentes/Card';
import { DialogoConfirmacao } from '../../componentes/DialogoConfirmacao';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Modal } from '../../componentes/Modal';
import { Tabela, type ColunaTabela } from '../../componentes/Tabela';
import { useToast } from '../../componentes/toastContexto';

const PAPEIS = ['COORDENADOR', 'VICE', 'SECRETARIO', 'TESOUREIRO', 'MEMBRO'] as const;
const NOME_DO_PAPEL: Record<string, string> = {
  COORDENADOR: 'Coordenador',
  VICE: 'Vice',
  SECRETARIO: 'Secretário',
  TESOUREIRO: 'Tesoureiro',
  MEMBRO: 'Membro',
};

function mensagem(erro: unknown, padrao: string): string {
  return erro instanceof ErroApi ? erro.message : padrao;
}

/** Membros da pastoral: ver papéis, adicionar, mudar papel e remover. Quem não coordena só vê. */
export function Membros() {
  const { pastoralId, acesso } = useAcessoDaPastoral();
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const consulta = useQuery({
    queryKey: getGetPastoraisPorPastoralIdMembrosQueryKey(pastoralId),
    queryFn: async () => (await getPastoraisPorPastoralIdMembros(pastoralId)).data,
  });
  const [adicionando, definirAdicionando] = useState(false);
  const [paraRemover, definirParaRemover] = useState<MembroPastoralDTO | null>(null);

  const atualizar = () => {
    void cliente.invalidateQueries({
      queryKey: getGetPastoraisPorPastoralIdMembrosQueryKey(pastoralId),
    });
  };

  const trocarPapel = useMutation({
    mutationFn: (args: { membro: MembroPastoralDTO; papel: (typeof PAPEIS)[number] }) =>
      putUsuariosPastoraisPorId(args.membro.usuarioPastoralId as number, {
        usuarioId: args.membro.usuario?.id as number,
        pastoralId,
        papel: args.papel,
      }),
    onSuccess: () => {
      mostrar('Papel alterado', 'sucesso');
      atualizar();
    },
    onError: (e: unknown) => {
      mostrar(mensagem(e, 'Não foi possível trocar o papel agora'), 'erro');
    },
  });

  const remover = useMutation({
    mutationFn: (id: number) => deleteUsuariosPastoraisPorId(id),
    onSuccess: () => {
      mostrar('Pessoa removida da pastoral', 'sucesso');
      atualizar();
    },
    onError: (e: unknown) => {
      mostrar(mensagem(e, 'Não foi possível remover agora'), 'erro');
    },
    onSettled: () => {
      definirParaRemover(null);
    },
  });

  const colunas: ColunaTabela<MembroPastoralDTO>[] = [
    { chave: 'nome', titulo: 'Nome', celula: (m) => m.usuario?.nome ?? '' },
    ...(acesso.veEmail
      ? [{ chave: 'email', titulo: 'E-mail', celula: (m: MembroPastoralDTO) => m.email ?? '' }]
      : []),
    {
      chave: 'papel',
      titulo: 'Papel',
      celula: (m) =>
        acesso.coordena && m.usuarioPastoralId !== undefined ? (
          <select
            aria-label={`Papel de ${m.usuario?.nome ?? ''}`}
            value={m.papel ?? 'MEMBRO'}
            onChange={(e) => {
              trocarPapel.mutate({ membro: m, papel: e.target.value as (typeof PAPEIS)[number] });
            }}
          >
            {PAPEIS.map((p) => (
              <option key={p} value={p}>
                {NOME_DO_PAPEL[p]}
              </option>
            ))}
          </select>
        ) : (
          (NOME_DO_PAPEL[m.papel ?? 'MEMBRO'] ?? '')
        ),
    },
    ...(acesso.coordena
      ? [
          {
            chave: 'acao',
            titulo: '',
            celula: (m: MembroPastoralDTO) => (
              <Botao
                variante="secundario"
                onClick={() => {
                  definirParaRemover(m);
                }}
              >
                Remover
              </Botao>
            ),
          },
        ]
      : []),
  ];

  return (
    <>
      <div className="cabecalho-pagina">
        <h1 className="titulo-pagina">Membros</h1>
        {acesso.coordena ? (
          <Botao
            onClick={() => {
              definirAdicionando(true);
            }}
          >
            Adicionar pessoa
          </Botao>
        ) : null}
      </div>
      {consulta.isPending ? <Esqueleto /> : null}
      {consulta.isError ? <EstadoVazio titulo="Não foi possível carregar os membros" /> : null}
      {consulta.data ? (
        <Card>
          {consulta.data.length === 0 ? (
            <EstadoVazio titulo="Nenhum membro ainda" />
          ) : (
            <Tabela
              titulo="Membros"
              linhas={consulta.data}
              chaveDaLinha={(m) => m.usuarioPastoralId ?? 0}
              colunas={colunas}
            />
          )}
        </Card>
      ) : null}
      {adicionando ? (
        <AdicionarPessoa
          pastoralId={pastoralId}
          onFechar={() => {
            definirAdicionando(false);
          }}
          onAdicionado={() => {
            definirAdicionando(false);
            atualizar();
          }}
        />
      ) : null}
      <DialogoConfirmacao
        aberto={paraRemover !== null}
        titulo="Remover esta pessoa da pastoral?"
        mensagem={`${paraRemover?.usuario?.nome ?? ''} deixa de fazer parte da pastoral. As escalas já marcadas continuam como estão.`}
        textoConfirmar="Remover"
        perigoso
        onCancelar={() => {
          definirParaRemover(null);
        }}
        onConfirmar={() => {
          if (paraRemover?.usuarioPastoralId !== undefined)
            remover.mutate(paraRemover.usuarioPastoralId);
        }}
      />
    </>
  );
}

function AdicionarPessoa({
  pastoralId,
  onFechar,
  onAdicionado,
}: {
  pastoralId: number;
  onFechar: () => void;
  onAdicionado: () => void;
}) {
  const { mostrar } = useToast();
  const [nome, definirNome] = useState('');
  const [buscado, definirBuscado] = useState('');
  const [papel, definirPapel] = useState<(typeof PAPEIS)[number]>('MEMBRO');
  const busca = useQuery({
    queryKey: ['usuarios', 'busca', buscado],
    queryFn: async () => (await getUsuariosBusca({ nome: buscado, size: 10 })).data,
    enabled: buscado.length >= 2,
  });
  const adicionar = useMutation({
    mutationFn: (pessoa: ReferenciaDTO) =>
      postUsuariosPastorais({ usuarioId: pessoa.id as number, pastoralId, papel }),
    onSuccess: () => {
      mostrar('Pessoa adicionada', 'sucesso');
      onAdicionado();
    },
    onError: (e: unknown) => {
      mostrar(mensagem(e, 'Não foi possível adicionar agora'), 'erro');
    },
  });

  return (
    <Modal titulo="Adicionar pessoa" aberto onFechar={onFechar}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          definirBuscado(nome.trim());
        }}
      >
        <Campo
          rotulo="Buscar pelo nome"
          value={nome}
          onChange={(e) => {
            definirNome(e.target.value);
          }}
        />
        <label className="campo">
          <span className="campo__rotulo">Papel</span>
          <select
            value={papel}
            onChange={(e) => {
              definirPapel(e.target.value as (typeof PAPEIS)[number]);
            }}
          >
            {PAPEIS.map((p) => (
              <option key={p} value={p}>
                {NOME_DO_PAPEL[p]}
              </option>
            ))}
          </select>
        </label>
        <Botao type="submit">Buscar</Botao>
      </form>
      {busca.isPending && buscado.length >= 2 ? <Esqueleto linhas={2} /> : null}
      {(busca.data?.content ?? []).map((pessoa) => (
        <div key={pessoa.id} className="candidato">
          <div>
            <strong>{pessoa.nome}</strong>
            <p className="texto-secundario">{pessoa.email}</p>
          </div>
          <Botao
            disabled={adicionar.isPending}
            onClick={() => {
              adicionar.mutate(pessoa);
            }}
          >
            Adicionar
          </Botao>
        </div>
      ))}
    </Modal>
  );
}
