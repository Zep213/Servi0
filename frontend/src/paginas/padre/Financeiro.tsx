import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getFinanceiroResumo, getGetFinanceiroResumoQueryKey } from '../../api/generated/servio';
import type { SaldoPorPastoralDTO } from '../../api/generated/modelos';
import { Botao } from '../../componentes/Botao';
import { Card } from '../../componentes/Card';
import { CardEstatistica } from '../../componentes/CardEstatistica';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Tabela, type ColunaTabela } from '../../componentes/Tabela';
import { deslocarMes, limitesDoMes, mesAtual, nomeDoMes } from '../../utilidades/datas';

const formatarReais = (valor: number | undefined) =>
  (valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

/** Financeiro consolidado da paróquia: total e por pastoral, só leitura (padre/admin). */
export function Financeiro() {
  const [mes, definirMes] = useState(mesAtual);
  const { de, ate } = limitesDoMes(mes);

  const resumo = useQuery({
    queryKey: getGetFinanceiroResumoQueryKey({ de, ate }),
    queryFn: async () => (await getFinanceiroResumo({ de, ate })).data,
  });

  const colunas: ColunaTabela<SaldoPorPastoralDTO>[] = [
    { chave: 'pastoral', titulo: 'Pastoral', celula: (p) => p.pastoralNome ?? '' },
    { chave: 'entradas', titulo: 'Entradas', celula: (p) => formatarReais(p.totalEntradas) },
    { chave: 'saidas', titulo: 'Saídas', celula: (p) => formatarReais(p.totalSaidas) },
    { chave: 'saldo', titulo: 'Saldo', celula: (p) => formatarReais(p.saldo) },
  ];

  return (
    <>
      <div className="cabecalho-pagina">
        <h1 className="titulo-pagina">Financeiro</h1>
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

      {resumo.isPending ? <Esqueleto linhas={4} /> : null}
      {resumo.isError ? (
        <EstadoVazio
          titulo="Não foi possível carregar o financeiro"
          texto="Verifique sua conexão e atualize a página."
        />
      ) : null}

      {resumo.data ? (
        <>
          <div className="grade-cards">
            <CardEstatistica rotulo="Entradas" valor={formatarReais(resumo.data.totalEntradas)} />
            <CardEstatistica rotulo="Saídas" valor={formatarReais(resumo.data.totalSaidas)} />
            <CardEstatistica rotulo="Saldo" valor={formatarReais(resumo.data.saldo)} />
          </div>
          <Card titulo="Por pastoral">
            {(resumo.data.porPastoral ?? []).length === 0 ? (
              <EstadoVazio titulo="Nenhum lançamento neste período" />
            ) : (
              <Tabela
                titulo="Por pastoral"
                linhas={resumo.data.porPastoral ?? []}
                chaveDaLinha={(p) => p.pastoralId ?? 0}
                colunas={colunas}
              />
            )}
          </Card>
        </>
      ) : null}
    </>
  );
}
