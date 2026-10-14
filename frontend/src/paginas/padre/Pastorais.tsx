import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import { getGetPastoraisQueryKey, getPastorais, postPastorais } from '../../api/generated/servio';
import type { PastoralResponseDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { Botao } from '../../componentes/Botao';
import { Campo } from '../../componentes/Campo';
import { Card } from '../../componentes/Card';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Modal } from '../../componentes/Modal';
import { Tabela, type ColunaTabela } from '../../componentes/Tabela';
import { useToast } from '../../componentes/toastContexto';

function mensagem(erro: unknown, padrao: string): string {
  return erro instanceof ErroApi ? erro.message : padrao;
}

/** Pastorais da paróquia: criar uma nova; nomear o coordenador é feito na página Membros dela. */
export function Pastorais() {
  const [criando, definirCriando] = useState(false);
  const cliente = useQueryClient();
  const { mostrar } = useToast();

  const pastorais = useQuery({
    queryKey: getGetPastoraisQueryKey({ size: 100, sort: ['nome'] }),
    queryFn: async () => (await getPastorais({ size: 100, sort: ['nome'] })).data,
  });

  const criar = useMutation({
    mutationFn: (nome: string) => postPastorais({ nome }),
    onSuccess: () => {
      mostrar('Pastoral criada', 'sucesso');
      definirCriando(false);
      void cliente.invalidateQueries({ queryKey: getGetPastoraisQueryKey() });
    },
    onError: (e: unknown) => {
      mostrar(mensagem(e, 'Não foi possível criar a pastoral agora'), 'erro');
    },
  });

  const colunas: ColunaTabela<PastoralResponseDTO>[] = [
    { chave: 'nome', titulo: 'Nome', celula: (p) => p.nome ?? '' },
    {
      chave: 'acao',
      titulo: '',
      celula: (p) => (
        <Link className="botao botao--secundario" to={`/pastoral/${String(p.id ?? '')}/membros`}>
          Membros
        </Link>
      ),
    },
  ];

  return (
    <>
      <div className="cabecalho-pagina">
        <h1 className="titulo-pagina">Pastorais</h1>
        <Botao
          onClick={() => {
            definirCriando(true);
          }}
        >
          Nova pastoral
        </Botao>
      </div>
      <p className="texto-corpo">
        Para nomear o coordenador, abra os membros da pastoral e adicione a pessoa com o papel
        Coordenador.
      </p>

      {pastorais.isPending ? <Esqueleto /> : null}
      {pastorais.isError ? (
        <EstadoVazio
          titulo="Não foi possível carregar as pastorais"
          texto="Verifique sua conexão e atualize a página."
        />
      ) : null}
      {pastorais.data ? (
        <Card>
          {(pastorais.data.content ?? []).length === 0 ? (
            <EstadoVazio titulo="Nenhuma pastoral cadastrada ainda" />
          ) : (
            <Tabela
              titulo="Pastorais"
              linhas={pastorais.data.content ?? []}
              chaveDaLinha={(p) => p.id ?? 0}
              colunas={colunas}
            />
          )}
        </Card>
      ) : null}

      {criando ? (
        <NovaPastoral
          enviando={criar.isPending}
          onFechar={() => {
            definirCriando(false);
          }}
          onEnviar={(nome) => {
            criar.mutate(nome);
          }}
        />
      ) : null}
    </>
  );
}

function NovaPastoral({
  enviando,
  onFechar,
  onEnviar,
}: {
  enviando: boolean;
  onFechar: () => void;
  onEnviar: (nome: string) => void;
}) {
  const [nome, definirNome] = useState('');
  const [erro, definirErro] = useState<string | undefined>();

  return (
    <Modal titulo="Nova pastoral" aberto onFechar={onFechar}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          const valor = nome.trim();
          if (!valor) {
            definirErro('Informe o nome da pastoral');
            return;
          }
          definirErro(undefined);
          onEnviar(valor);
        }}
        noValidate
      >
        <Campo
          rotulo="Nome"
          value={nome}
          erro={erro}
          onChange={(e) => {
            definirNome(e.target.value);
          }}
        />
        <Botao type="submit" disabled={enviando}>
          Criar pastoral
        </Botao>
      </form>
    </Modal>
  );
}
