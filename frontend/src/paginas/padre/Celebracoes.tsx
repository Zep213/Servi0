import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router';
import type { z } from 'zod';
import {
  getComunidades,
  getGetCelebracoesQueryKey,
  getGetComunidadesQueryKey,
  getCelebracoes,
  postCelebracoes,
} from '../../api/generated/servio';
import type { CelebracaoResponseDTO, ComunidadeResponseDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { Botao } from '../../componentes/Botao';
import { Card } from '../../componentes/Card';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Modal } from '../../componentes/Modal';
import { Tabela, type ColunaTabela } from '../../componentes/Tabela';
import { useToast } from '../../componentes/toastContexto';
import {
  dataPorExtenso,
  deslocarMes,
  horaCurta,
  limitesDoMes,
  mesAtual,
  nomeDoMes,
} from '../../utilidades/datas';
import { CamposCelebracao } from './CamposCelebracao';
import {
  esquemaCelebracao,
  NOME_DO_TIPO,
  VALORES_PADRAO_CELEBRACAO,
  type DadosCelebracao,
} from './celebracaoCampos';

function mensagem(erro: unknown, padrao: string): string {
  return erro instanceof ErroApi ? erro.message : padrao;
}

/** Dias do mês (AAAA-MM-DD), completando a primeira e a última semana com dias de outros meses. */
function diasDoCalendario(mes: string): string[] {
  const { de, ate } = limitesDoMes(mes);
  const [anoDe, mesDe, diaDe] = de.split('-').map(Number) as [number, number, number];
  const [anoAte, mesAte, diaAte] = ate.split('-').map(Number) as [number, number, number];
  const primeiro = new Date(anoDe, mesDe - 1, diaDe);
  const ultimo = new Date(anoAte, mesAte - 1, diaAte);
  const inicio = new Date(primeiro);
  inicio.setDate(inicio.getDate() - inicio.getDay());
  const fim = new Date(ultimo);
  fim.setDate(fim.getDate() + (6 - fim.getDay()));

  const dias: string[] = [];
  for (const d = new Date(inicio); d <= fim; d.setDate(d.getDate() + 1)) {
    const ano = String(d.getFullYear());
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    dias.push(`${ano}-${m}-${dia}`);
  }
  return dias;
}

const DIAS_DA_SEMANA = ['Dom', 'Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb'];

/** Celebrações da paróquia: calendário do mês e lista, com a criação de novas (só padre/admin). */
export function Celebracoes() {
  const [mes, definirMes] = useState(mesAtual);
  const [criando, definirCriando] = useState(false);
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const { de, ate } = limitesDoMes(mes);
  const params = { de, ate, size: 200, sort: ['data', 'hora'] };

  const celebracoes = useQuery({
    queryKey: getGetCelebracoesQueryKey(params),
    queryFn: async () => (await getCelebracoes(params)).data,
  });
  const comunidades = useQuery({
    queryKey: getGetComunidadesQueryKey({ size: 100 }),
    queryFn: async () => (await getComunidades({ size: 100 })).data,
  });

  const listaDeComunidades = comunidades.data?.content ?? [];
  const nomeDaComunidade = (id: number | undefined) =>
    listaDeComunidades.find((c) => c.id === id)?.nome ?? '';

  const porDia = useMemo(() => {
    const mapa = new Map<string, CelebracaoResponseDTO[]>();
    for (const c of celebracoes.data?.content ?? []) {
      if (!c.data) continue;
      const lista = mapa.get(c.data) ?? [];
      lista.push(c);
      mapa.set(c.data, lista);
    }
    return mapa;
  }, [celebracoes.data]);

  const criar = useMutation({
    mutationFn: (dados: DadosCelebracao) =>
      postCelebracoes({
        comunidadeId: dados.comunidadeId,
        data: dados.data,
        hora: dados.hora,
        tipoData: dados.tipoData,
        tipo: dados.tipo,
        titulo: dados.titulo?.trim() || undefined,
      }),
    onSuccess: () => {
      mostrar('Celebração criada', 'sucesso');
      definirCriando(false);
      void cliente.invalidateQueries({ queryKey: getGetCelebracoesQueryKey() });
    },
    onError: (e: unknown) => {
      mostrar(mensagem(e, 'Não foi possível criar a celebração agora'), 'erro');
    },
  });

  const colunas: ColunaTabela<CelebracaoResponseDTO>[] = [
    {
      chave: 'data',
      titulo: 'Data',
      celula: (c) => `${dataPorExtenso(c.data)} · ${horaCurta(c.hora)}`,
    },
    { chave: 'comunidade', titulo: 'Comunidade', celula: (c) => nomeDaComunidade(c.comunidadeId) },
    { chave: 'tipo', titulo: 'Tipo', celula: (c) => NOME_DO_TIPO[c.tipo ?? ''] ?? '' },
    { chave: 'titulo', titulo: 'Título', celula: (c) => c.titulo ?? '' },
    {
      chave: 'acao',
      titulo: '',
      celula: (c) => (
        <Link className="botao botao--secundario" to={`/celebracoes/${String(c.id ?? '')}`}>
          Ver
        </Link>
      ),
    },
  ];

  return (
    <>
      <div className="cabecalho-pagina">
        <h1 className="titulo-pagina">Celebrações</h1>
        <Botao
          onClick={() => {
            definirCriando(true);
          }}
        >
          Nova celebração
        </Botao>
      </div>
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

      {celebracoes.isPending ? <Esqueleto linhas={6} /> : null}
      {celebracoes.isError ? (
        <EstadoVazio
          titulo="Não foi possível carregar as celebrações"
          texto="Verifique sua conexão e atualize a página."
        />
      ) : null}

      {celebracoes.data ? (
        <>
          <Card titulo="Calendário">
            <div className="calendario" role="table" aria-label={`Celebrações de ${nomeDoMes(mes)}`}>
              <div className="calendario__semana" role="row">
                {DIAS_DA_SEMANA.map((d) => (
                  <span key={d} className="calendario__cabecalho-dia" role="columnheader">
                    {d}
                  </span>
                ))}
              </div>
              {Array.from({ length: diasDoCalendario(mes).length / 7 }, (_, semana) => (
                <div key={semana} className="calendario__semana" role="row">
                  {diasDoCalendario(mes)
                    .slice(semana * 7, semana * 7 + 7)
                    .map((dia) => (
                      <div
                        key={dia}
                        className={`calendario__dia${dia.startsWith(mes) ? '' : ' calendario__dia--fora'}`}
                        role="cell"
                      >
                        <span className="calendario__numero">{Number(dia.slice(8, 10))}</span>
                        {(porDia.get(dia) ?? []).map((c) => (
                          <Link
                            key={c.id}
                            className="calendario__celebracao"
                            to={`/celebracoes/${String(c.id ?? '')}`}
                          >
                            {horaCurta(c.hora)} {c.titulo ?? NOME_DO_TIPO[c.tipo ?? ''] ?? ''}
                          </Link>
                        ))}
                      </div>
                    ))}
                </div>
              ))}
            </div>
          </Card>
          <Card titulo="Lista">
            {(celebracoes.data.content ?? []).length === 0 ? (
              <EstadoVazio titulo="Nenhuma celebração neste mês" />
            ) : (
              <Tabela
                titulo="Celebrações do mês"
                linhas={celebracoes.data.content ?? []}
                chaveDaLinha={(c) => c.id ?? 0}
                colunas={colunas}
              />
            )}
          </Card>
        </>
      ) : null}

      {criando ? (
        <NovaCelebracao
          comunidades={listaDeComunidades}
          enviando={criar.isPending}
          onFechar={() => {
            definirCriando(false);
          }}
          onEnviar={(dados) => {
            criar.mutate(dados);
          }}
        />
      ) : null}
    </>
  );
}

function NovaCelebracao({
  comunidades,
  enviando,
  onFechar,
  onEnviar,
}: {
  comunidades: ComunidadeResponseDTO[];
  enviando: boolean;
  onFechar: () => void;
  onEnviar: (dados: DadosCelebracao) => void;
}) {
  const formulario = useForm<z.input<typeof esquemaCelebracao>, unknown, DadosCelebracao>({
    resolver: zodResolver(esquemaCelebracao),
    defaultValues: {
      ...VALORES_PADRAO_CELEBRACAO,
      comunidadeId: comunidades[0]?.id ?? 0,
    },
  });
  const enviar = formulario.handleSubmit((dados) => {
    onEnviar(dados);
  });

  if (comunidades.length === 0) {
    return (
      <Modal titulo="Nova celebração" aberto onFechar={onFechar}>
        <EstadoVazio
          titulo="Nenhuma comunidade cadastrada"
          texto="Cadastre uma comunidade antes de criar celebrações."
        />
      </Modal>
    );
  }

  return (
    <Modal titulo="Nova celebração" aberto onFechar={onFechar}>
      <form
        onSubmit={(e) => {
          void enviar(e);
        }}
        noValidate
      >
        <CamposCelebracao formulario={formulario} comunidades={comunidades} />
        <Botao type="submit" disabled={enviando}>
          Criar celebração
        </Botao>
      </form>
    </Modal>
  );
}
