import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  deleteUsuariosPastoraisPorId,
  getGetPastoraisPorPastoralIdMembrosQueryKey,
  getPastoraisPorPastoralIdMembros,
  getUsuariosBusca,
  postUsuarios,
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

const esquemaContaNova = z.object({
  nome: z.string().trim().min(1, 'Informe o nome'),
  email: z.email('Informe um e-mail válido'),
  senha: z
    .string()
    .min(8, 'A senha precisa de pelo menos 8 caracteres')
    .max(72, 'A senha pode ter no máximo 72 caracteres'),
});
type DadosContaNova = z.infer<typeof esquemaContaNova>;
const CAMPOS_CONTA_NOVA = ['nome', 'email', 'senha'] as const;

function SeletorPapel({
  papel,
  onMudar,
}: {
  papel: (typeof PAPEIS)[number];
  onMudar: (papel: (typeof PAPEIS)[number]) => void;
}) {
  return (
    <label className="campo">
      <span className="campo__rotulo">Papel</span>
      <select
        value={papel}
        onChange={(e) => {
          onMudar(e.target.value as (typeof PAPEIS)[number]);
        }}
      >
        {PAPEIS.map((p) => (
          <option key={p} value={p}>
            {NOME_DO_PAPEL[p]}
          </option>
        ))}
      </select>
    </label>
  );
}

function CriarConta({
  pastoralId,
  onVoltar,
  onAdicionado,
}: {
  pastoralId: number;
  onVoltar: () => void;
  onAdicionado: () => void;
}) {
  const { mostrar } = useToast();
  const [papel, definirPapel] = useState<(typeof PAPEIS)[number]>('MEMBRO');
  const formulario = useForm<DadosContaNova>({
    resolver: zodResolver(esquemaContaNova),
    defaultValues: { nome: '', email: '', senha: '' },
  });

  const criar = useMutation({
    mutationFn: async (dados: DadosContaNova) => {
      const conta = await postUsuarios({
        nome: dados.nome,
        email: dados.email,
        senha: dados.senha,
        perfil: 'SERVIDOR',
      });
      try {
        await postUsuariosPastorais({ usuarioId: conta.data.id as number, pastoralId, papel });
      } catch (erro) {
        // A conta já existe: avisar para buscá-la pelo nome em vez de criar de novo.
        throw new ErroApi(
          erro instanceof ErroApi ? erro.status : 0,
          `A conta foi criada, mas não entrou na pastoral: ${mensagem(erro, 'tente de novo')}. Busque pelo nome para adicionar.`,
        );
      }
    },
    onSuccess: () => {
      mostrar('Conta criada e pessoa adicionada', 'sucesso');
      onAdicionado();
    },
    onError: (erro: unknown) => {
      // 409 do backend é sempre e-mail já usado por outra conta ativa.
      if (erro instanceof ErroApi && erro.status === 409) {
        formulario.setError('email', { message: erro.message });
        return;
      }
      const porCampo = erro instanceof ErroApi ? erro.erros : {};
      const conhecidos = CAMPOS_CONTA_NOVA.filter((c) => porCampo[c]);
      for (const campo of conhecidos) formulario.setError(campo, { message: porCampo[campo] });
      if (conhecidos.length === 0) mostrar(mensagem(erro, 'Não foi possível criar agora'), 'erro');
    },
  });

  const enviar = formulario.handleSubmit((dados) => {
    criar.mutate(dados);
  });
  const erros = formulario.formState.errors;

  return (
    <form
      onSubmit={(e) => {
        void enviar(e);
      }}
      noValidate
    >
      <Campo rotulo="Nome completo" erro={erros.nome?.message} {...formulario.register('nome')} />
      <Campo
        rotulo="E-mail"
        type="email"
        autoComplete="off"
        erro={erros.email?.message}
        {...formulario.register('email')}
      />
      <Campo
        rotulo="Senha inicial"
        type="password"
        autoComplete="new-password"
        ajuda="Passe esta senha para a pessoa. Ela pode trocar depois, em Trocar senha."
        erro={erros.senha?.message}
        {...formulario.register('senha')}
      />
      <SeletorPapel papel={papel} onMudar={definirPapel} />
      <div className="acoes-linha">
        <Botao type="submit" disabled={criar.isPending}>
          Criar conta e adicionar
        </Botao>
        <Botao type="button" variante="secundario" onClick={onVoltar}>
          Voltar para a busca
        </Botao>
      </div>
    </form>
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
  const [criandoConta, definirCriandoConta] = useState(false);
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

  if (criandoConta) {
    return (
      <Modal titulo="Criar conta nova" aberto onFechar={onFechar}>
        <CriarConta
          pastoralId={pastoralId}
          onVoltar={() => {
            definirCriandoConta(false);
          }}
          onAdicionado={onAdicionado}
        />
      </Modal>
    );
  }

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
        <SeletorPapel papel={papel} onMudar={definirPapel} />
        <div className="acoes-linha">
          <Botao type="submit">Buscar</Botao>
          <Botao
            type="button"
            variante="secundario"
            onClick={() => {
              definirCriandoConta(true);
            }}
          >
            Criar conta nova
          </Botao>
        </div>
      </form>
      {busca.isPending && buscado.length >= 2 ? <Esqueleto linhas={2} /> : null}
      {busca.isSuccess && (busca.data.content ?? []).length === 0 ? (
        <EstadoVazio
          titulo="Ninguém com esse nome"
          texto="Se a pessoa ainda não tem conta, use Criar conta nova."
        />
      ) : null}
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
