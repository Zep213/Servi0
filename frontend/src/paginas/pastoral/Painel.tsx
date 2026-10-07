import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router';
import {
  getPastoraisPorPastoralIdPainel,
  getGetPastoraisPorPastoralIdPainelQueryKey,
  postAlteracoesPendentesPorIdConfirmar,
  postAlteracoesPendentesPorIdDesfazer,
  postPastoraisPorPastoralIdCelebracoesPorCelebracaoIdSortear,
} from '../../api/generated/servio';
import type { CelebracaoPainelDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { useAcessoDaPastoral } from '../../auth/useAcesso';
import { Botao } from '../../componentes/Botao';
import { Card } from '../../componentes/Card';
import { CardEstatistica } from '../../componentes/CardEstatistica';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { SeloStatus, type StatusSelo } from '../../componentes/SeloStatus';
import { Tabela, type ColunaTabela } from '../../componentes/Tabela';
import { useToast } from '../../componentes/toastContexto';
import {
  dataPorExtenso,
  deslocarMes,
  horaCurta,
  mesAtual,
  nomeDoMes,
  prazoLegivel,
} from '../../utilidades/datas';

const STATUS_DA_CELEBRACAO: Record<string, { selo: StatusSelo; texto: string }> = {
  NAO_INICIADO: { selo: 'neutro', texto: 'Ainda não começou' },
  PENDENTE: { selo: 'atencao', texto: 'Em andamento' },
  COMPLETO: { selo: 'confirmado', texto: 'Completo' },
};

/** Painel do coordenador: cartões do mês, celebrações com o que falta, e o que pede atenção. */
export function Painel() {
  const { pastoralId, acesso } = useAcessoDaPastoral();
  const [mes, definirMes] = useState(mesAtual);
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const params = { mes };
  const consulta = useQuery({
    queryKey: getGetPastoraisPorPastoralIdPainelQueryKey(pastoralId, params),
    queryFn: async () => (await getPastoraisPorPastoralIdPainel(pastoralId, params)).data,
  });

  const atualizar = () => {
    void cliente.invalidateQueries({
      queryKey: getGetPastoraisPorPastoralIdPainelQueryKey(pastoralId),
    });
  };

  const sortearCelebracao = useMutation({
    mutationFn: (celebracaoId: number) =>
      postPastoraisPorPastoralIdCelebracoesPorCelebracaoIdSortear(pastoralId, celebracaoId),
    onSuccess: (resposta) => {
      const incompletas = resposta.data.vagasIncompletas?.length ?? 0;
      mostrar(
        incompletas > 0
          ? `Sorteio feito. ${String(incompletas)} vaga(s) ficaram com gente faltando`
          : 'Sorteio feito. Os convites já foram enviados',
        incompletas > 0 ? 'info' : 'sucesso',
      );
      atualizar();
    },
    onError: (erro: unknown) => {
      mostrar(erro instanceof ErroApi ? erro.message : 'Não foi possível sortear agora', 'erro');
    },
  });

  const decidirAlteracao = useMutation({
    mutationFn: ({ id, confirmar }: { id: number; confirmar: boolean }) =>
      confirmar
        ? postAlteracoesPendentesPorIdConfirmar(id)
        : postAlteracoesPendentesPorIdDesfazer(id),
    onSuccess: (_r, { confirmar }) => {
      mostrar(confirmar ? 'Alteração aprovada' : 'Alteração desfeita', 'sucesso');
      atualizar();
    },
    onError: (erro: unknown) => {
      mostrar(erro instanceof ErroApi ? erro.message : 'Não foi possível decidir agora', 'erro');
    },
  });

  const dados = consulta.data;
  const colunas: ColunaTabela<CelebracaoPainelDTO>[] = [
    {
      chave: 'data',
      titulo: 'Data',
      celula: (c) => `${dataPorExtenso(c.data)} · ${horaCurta(c.hora)}`,
    },
    { chave: 'celebracao', titulo: 'Celebração', celula: (c) => c.titulo ?? '' },
    {
      chave: 'vagas',
      titulo: 'Vagas',
      celula: (c) =>
        `${String(c.ocupadas ?? 0)} de ${String(c.vagasTotais ?? 0)}${c.aguardandoQuantidade ? ' (faltam quantidades)' : ''}`,
    },
    {
      chave: 'status',
      titulo: 'Situação',
      celula: (c) => {
        const st = STATUS_DA_CELEBRACAO[c.status ?? ''] ?? { selo: 'neutro' as const, texto: '' };
        return <SeloStatus status={st.selo} texto={st.texto} />;
      },
    },
    {
      chave: 'acoes',
      titulo: '',
      celula: (c) => (
        <div className="acoes-linha">
          <Link
            className="botao botao--secundario"
            to={`/pastoral/${String(pastoralId)}/celebracoes/${String(c.id)}`}
          >
            {acesso.escala ? 'Escala' : 'Ver escala'}
          </Link>
          {acesso.escala && c.tipo === 'MISSA_DOMINICAL' && c.id !== undefined ? (
            <Botao
              variante="secundario"
              disabled={sortearCelebracao.isPending}
              onClick={() => {
                sortearCelebracao.mutate(c.id as number);
              }}
            >
              Sortear
            </Botao>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <>
      <div className="cabecalho-pagina">
        <h1 className="titulo-pagina">Painel</h1>
        <div className="mes-navegacao" role="group" aria-label="Mês">
          <Botao
            variante="secundario"
            aria-label="Mês anterior"
            onClick={() => {
              definirMes(deslocarMes(mes, -1));
            }}
          >
            ‹
          </Botao>
          <span className="mes-navegacao__nome">{nomeDoMes(mes)}</span>
          <Botao
            variante="secundario"
            aria-label="Próximo mês"
            onClick={() => {
              definirMes(deslocarMes(mes, 1));
            }}
          >
            ›
          </Botao>
        </div>
      </div>

      {consulta.isPending ? <Esqueleto linhas={6} /> : null}
      {consulta.isError ? (
        <EstadoVazio
          titulo="Não foi possível carregar o painel"
          texto="Verifique sua conexão e atualize a página."
        />
      ) : null}

      {dados?.cards ? (
        <div className="grade-cards">
          <CardEstatistica rotulo="Vagas no mês" valor={dados.cards.vagasTotais ?? 0} />
          <CardEstatistica rotulo="Ocupadas" valor={dados.cards.vagasOcupadas ?? 0} />
          <CardEstatistica
            rotulo="Aguardando resposta"
            valor={dados.cards.convitesPendentes ?? 0}
          />
          <CardEstatistica
            rotulo="Alterações para decidir"
            valor={dados.cards.alteracoesPendentes ?? 0}
          />
        </div>
      ) : null}

      {dados ? (
        <Card titulo="Celebrações do mês">
          {(dados.celebracoes ?? []).length === 0 ? (
            <EstadoVazio titulo="Nenhuma celebração com vagas neste mês" />
          ) : (
            <Tabela
              titulo="Celebrações do mês"
              linhas={dados.celebracoes ?? []}
              chaveDaLinha={(c) => c.id ?? 0}
              colunas={colunas}
            />
          )}
        </Card>
      ) : null}

      {dados?.pendencias ? (
        <Card titulo="Pede atenção">
          {(dados.pendencias.convitesVencendo ?? []).map((c) => (
            <p key={`v-${String(c.alocacaoId)}`} className="pendencia">
              <strong>{c.nome}</strong> ainda não respondeu sobre {c.titulo}. Prazo:{' '}
              {prazoLegivel(c.prazo)}.
            </p>
          ))}
          {(dados.pendencias.recusasSemSubstituto ?? []).map((r) => (
            <p key={`r-${String(r.alocacaoId)}`} className="pendencia">
              {r.status === 'RECUSADA' ? 'Recusou' : 'Não respondeu a tempo'}:{' '}
              <strong>{r.nome}</strong> em {r.titulo}. Faltam {r.faltam} pessoa(s).{' '}
              <Link to={`/pastoral/${String(pastoralId)}/celebracoes/${String(r.celebracaoId)}`}>
                Escolher alguém
              </Link>
            </p>
          ))}
          {(dados.pendencias.alteracoesAguardando ?? []).map((a) => (
            <div key={`a-${String(a.id)}`} className="pendencia">
              <p>
                Vice trocou <strong>{a.usuarioAnterior}</strong> por{' '}
                <strong>{a.usuarioNovo}</strong> ({a.funcao}).
              </p>
              {acesso.coordena && a.id !== undefined ? (
                <div className="acoes-linha">
                  <Botao
                    onClick={() => {
                      decidirAlteracao.mutate({ id: a.id as number, confirmar: true });
                    }}
                  >
                    Aprovar
                  </Botao>
                  <Botao
                    variante="secundario"
                    onClick={() => {
                      decidirAlteracao.mutate({ id: a.id as number, confirmar: false });
                    }}
                  >
                    Desfazer
                  </Botao>
                </div>
              ) : null}
            </div>
          ))}
          {(dados.pendencias.convitesVencendo ?? []).length +
            (dados.pendencias.recusasSemSubstituto ?? []).length +
            (dados.pendencias.alteracoesAguardando ?? []).length ===
          0 ? (
            <EstadoVazio titulo="Nada pede atenção agora" />
          ) : null}
        </Card>
      ) : null}
    </>
  );
}
