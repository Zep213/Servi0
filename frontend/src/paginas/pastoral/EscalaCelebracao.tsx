import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useParams } from 'react-router';
import {
  getCelebracoesPorId,
  getGetCelebracoesPorIdQueryKey,
  getGetPastoraisPorPastoralIdCelebracoesPorCelebracaoIdEscalaQueryKey,
  getPastoraisPorPastoralIdCelebracoesPorCelebracaoIdEscala,
  getVagasPorIdCandidatos,
  postAlocacoesPorIdReenviarConvite,
  postAlocacoesPorIdSubstituir,
  postPastoraisPorPastoralIdCelebracoesPorCelebracaoIdSortear,
  postVagasPorIdEscalar,
  postVagasPorIdSortear,
  putVagasPorId,
} from '../../api/generated/servio';
import type { AlocacaoEscalaDTO, CandidatoDTO, VagaEscalaDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { useAcessoDaPastoral } from '../../auth/useAcesso';
import { Botao } from '../../componentes/Botao';
import { Campo } from '../../componentes/Campo';
import { Card } from '../../componentes/Card';
import { DialogoConfirmacao } from '../../componentes/DialogoConfirmacao';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Modal } from '../../componentes/Modal';
import { SeloStatus, type StatusSelo } from '../../componentes/SeloStatus';
import { useToast } from '../../componentes/toastContexto';
import { dataPorExtenso, horaCurta } from '../../utilidades/datas';

const SELO: Record<string, { selo: StatusSelo; texto: string }> = {
  PENDENTE: { selo: 'pendente', texto: 'Aguardando resposta' },
  ACEITA: { selo: 'confirmado', texto: 'Confirmado' },
  RECUSADA: { selo: 'recusado', texto: 'Recusou' },
  EXPIRADA: { selo: 'expirado', texto: 'Prazo encerrado' },
};

const ORIGEM: Record<string, string> = { SORTEIO: 'sorteio', COORDENADOR: 'escolhido à mão' };

/** Escala de uma celebração: por vaga, quem está escalado e o que dá para fazer com cada um. */
export function EscalaCelebracao() {
  const { celebracaoId } = useParams();
  const { pastoralId, acesso } = useAcessoDaPastoral();
  const idCelebracao = Number(celebracaoId);
  const cliente = useQueryClient();
  const { mostrar } = useToast();

  const celebracao = useQuery({
    queryKey: getGetCelebracoesPorIdQueryKey(idCelebracao),
    queryFn: async () => (await getCelebracoesPorId(idCelebracao)).data,
  });
  const escala = useQuery({
    queryKey: getGetPastoraisPorPastoralIdCelebracoesPorCelebracaoIdEscalaQueryKey(
      pastoralId,
      idCelebracao,
    ),
    queryFn: async () =>
      (await getPastoraisPorPastoralIdCelebracoesPorCelebracaoIdEscala(pastoralId, idCelebracao))
        .data,
  });

  const atualizar = () => {
    void cliente.invalidateQueries({
      queryKey: getGetPastoraisPorPastoralIdCelebracoesPorCelebracaoIdEscalaQueryKey(
        pastoralId,
        idCelebracao,
      ),
    });
  };

  const erro = (e: unknown) => {
    mostrar(e instanceof ErroApi ? e.message : 'Não foi possível concluir agora', 'erro');
  };

  const sortearCelebracao = useMutation({
    mutationFn: () =>
      postPastoraisPorPastoralIdCelebracoesPorCelebracaoIdSortear(pastoralId, idCelebracao),
    onSuccess: (r) => {
      const faltam = r.data.vagasIncompletas ?? [];
      mostrar(
        faltam.length > 0
          ? `Sorteio feito. Faltam pessoas em ${String(faltam.length)} vaga(s)`
          : 'Sorteio feito',
        faltam.length > 0 ? 'info' : 'sucesso',
      );
      atualizar();
    },
    onError: erro,
  });

  const reenviar = useMutation({
    mutationFn: (alocacaoId: number) => postAlocacoesPorIdReenviarConvite(alocacaoId),
    onSuccess: () => {
      mostrar('Convite reenviado', 'sucesso');
      atualizar();
    },
    onError: erro,
  });

  const [escolhendoVaga, definirEscolhendoVaga] = useState<VagaEscalaDTO | null>(null);
  const [trocando, definirTrocando] = useState<AlocacaoEscalaDTO | null>(null);
  const [editando, definirEditando] = useState<VagaEscalaDTO | null>(null);

  const dados = escala.data;
  const info = celebracao.data;

  return (
    <>
      <h1 className="titulo-pagina">Escala</h1>
      {info ? (
        <p className="texto-corpo">
          <strong>{info.titulo}</strong> · {dataPorExtenso(info.data)} · {horaCurta(info.hora)}
        </p>
      ) : null}
      {acesso.escala && info?.tipo === 'MISSA_DOMINICAL' ? (
        <div className="acoes-linha acoes-linha--pagina">
          <Botao
            disabled={sortearCelebracao.isPending}
            onClick={() => {
              sortearCelebracao.mutate();
            }}
          >
            Sortear esta celebração
          </Botao>
        </div>
      ) : null}

      {escala.isPending ? <Esqueleto linhas={6} /> : null}
      {escala.isError ? (
        <EstadoVazio
          titulo="Não foi possível carregar a escala"
          texto="Verifique sua conexão e atualize a página."
        />
      ) : null}
      {dados && (dados.vagas ?? []).length === 0 ? (
        <EstadoVazio titulo="Esta celebração não tem vagas para a pastoral" />
      ) : null}

      {(dados?.vagas ?? []).map((vaga) => (
        <Card
          key={vaga.vagaId}
          titulo={vaga.funcao?.nome ?? 'Função'}
          acoes={
            acesso.escala && vaga.vagaId !== undefined ? (
              <>
                <Botao
                  variante="secundario"
                  onClick={() => {
                    definirEditando(vaga);
                  }}
                >
                  Editar vaga
                </Botao>
                <Botao
                  variante="secundario"
                  onClick={() => {
                    definirEscolhendoVaga(vaga);
                  }}
                >
                  Escolher pessoa
                </Botao>
              </>
            ) : null
          }
        >
          <p className="texto-corpo">
            {vaga.quantidade != null
              ? `${String(vaga.quantidade)} pessoa(s)`
              : 'Quantidade ainda não definida'}
            {vaga.horarioChegada ? ` · chegar até ${horaCurta(vaga.horarioChegada)}` : ''}
            {vaga.observacao ? ` · ${vaga.observacao}` : ''}
          </p>
          {(vaga.alocacoes ?? []).length === 0 ? (
            <p className="texto-corpo">Ninguém escalado ainda.</p>
          ) : (
            <ul className="lista-escalados">
              {(vaga.alocacoes ?? []).map((a) => {
                const st = SELO[a.status ?? ''] ?? { selo: 'neutro' as const, texto: '' };
                return (
                  <li key={a.id} className="lista-escalados__item">
                    <span className="lista-escalados__nome">{a.usuario?.nome}</span>
                    <SeloStatus status={st.selo} texto={st.texto} />
                    <span className="texto-secundario">{a.origem ? ORIGEM[a.origem] : ''}</span>
                    {acesso.escala && a.id !== undefined ? (
                      <span className="acoes-linha">
                        {a.status === 'PENDENTE' ? (
                          <Botao
                            variante="secundario"
                            disabled={reenviar.isPending}
                            onClick={() => {
                              reenviar.mutate(a.id as number);
                            }}
                          >
                            Reenviar convite
                          </Botao>
                        ) : null}
                        {a.status === 'PENDENTE' || a.status === 'ACEITA' ? (
                          <Botao
                            variante="secundario"
                            onClick={() => {
                              definirTrocando(a);
                            }}
                          >
                            Trocar
                          </Botao>
                        ) : null}
                      </span>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          )}
          {acesso.escala && vaga.vagaId !== undefined && vaga.quantidade ? (
            <SorteioDaVaga vagaId={vaga.vagaId} onFeito={atualizar} />
          ) : null}
        </Card>
      ))}

      {escolhendoVaga?.vagaId !== undefined ? (
        <EscolherPessoa
          vagaId={escolhendoVaga.vagaId}
          onFechar={() => {
            definirEscolhendoVaga(null);
          }}
          onFeito={() => {
            definirEscolhendoVaga(null);
            atualizar();
          }}
          podeForcar={acesso.forca}
          alvo={{ tipo: 'nova' }}
        />
      ) : null}
      {trocando?.id !== undefined ? (
        <EscolherPessoa
          vagaId={vagaDaAlocacao(dados?.vagas ?? [], trocando.id)}
          onFechar={() => {
            definirTrocando(null);
          }}
          onFeito={() => {
            definirTrocando(null);
            atualizar();
          }}
          podeForcar={acesso.forca}
          alvo={{ tipo: 'substituir', alocacaoId: trocando.id }}
        />
      ) : null}
      {editando ? (
        <EditarVaga
          vaga={editando}
          celebracaoId={idCelebracao}
          onFechar={() => {
            definirEditando(null);
          }}
          onSalvo={() => {
            definirEditando(null);
            atualizar();
          }}
        />
      ) : null}
    </>
  );
}

function vagaDaAlocacao(vagas: VagaEscalaDTO[], alocacaoId: number): number {
  for (const v of vagas) {
    if ((v.alocacoes ?? []).some((a) => a.id === alocacaoId) && v.vagaId !== undefined)
      return v.vagaId;
  }
  return 0;
}

function SorteioDaVaga({ vagaId, onFeito }: { vagaId: number; onFeito: () => void }) {
  const { mostrar } = useToast();
  const sortear = useMutation({
    mutationFn: () => postVagasPorIdSortear(vagaId),
    onSuccess: (r) => {
      const faltam = r.data.vagasIncompletas?.[0]?.faltam ?? 0;
      mostrar(
        faltam > 0 ? `Faltam ${String(faltam)} pessoa(s) nesta vaga` : 'Sorteio feito',
        faltam > 0 ? 'info' : 'sucesso',
      );
      onFeito();
    },
    onError: (e: unknown) => {
      mostrar(e instanceof ErroApi ? e.message : 'Não foi possível sortear agora', 'erro');
    },
  });
  return (
    <div className="acoes-linha">
      <Botao
        variante="secundario"
        disabled={sortear.isPending}
        onClick={() => {
          sortear.mutate();
        }}
      >
        Sortear esta vaga
      </Botao>
    </div>
  );
}

interface AlvoEscolha {
  tipo: 'nova' | 'substituir';
  alocacaoId?: number;
}

/**
 * Lista de candidatos com o motivo de quem está impedido. "Escalar mesmo assim" aparece só para quem
 * pode forçar, e pede confirmação repetindo o motivo.
 */
function EscolherPessoa({
  vagaId,
  alvo,
  podeForcar,
  onFechar,
  onFeito,
}: {
  vagaId: number;
  alvo: AlvoEscolha;
  podeForcar: boolean;
  onFechar: () => void;
  onFeito: () => void;
}) {
  const { mostrar } = useToast();
  const [forcando, definirForcando] = useState<CandidatoDTO | null>(null);
  const candidatos = useQuery({
    queryKey: ['vagas', vagaId, 'candidatos'],
    queryFn: async () => (await getVagasPorIdCandidatos(vagaId)).data,
  });

  const escolher = useMutation({
    mutationFn: (args: { usuarioId: number; forcar: boolean }) =>
      alvo.tipo === 'nova'
        ? postVagasPorIdEscalar(vagaId, { usuarioId: args.usuarioId, forcar: args.forcar })
        : postAlocacoesPorIdSubstituir(alvo.alocacaoId as number, {
            usuarioId: args.usuarioId,
            forcar: args.forcar,
          }),
    onSuccess: () => {
      mostrar(
        alvo.tipo === 'nova' ? 'Pessoa escalada e convidada' : 'Pessoa trocada e convidada',
        'sucesso',
      );
      onFeito();
    },
    onError: (e: unknown) => {
      mostrar(e instanceof ErroApi ? e.message : 'Não foi possível escalar agora', 'erro');
    },
  });

  return (
    <>
      <Modal
        titulo={alvo.tipo === 'nova' ? 'Escolher pessoa' : 'Trocar pessoa'}
        aberto
        onFechar={onFechar}
      >
        {candidatos.isPending ? <Esqueleto /> : null}
        {(candidatos.data ?? []).map((c) => (
          <div key={c.usuario?.id} className="candidato">
            <div>
              <strong>{c.usuario?.nome}</strong>
              {c.elegivel ? null : (
                <p className="texto-secundario">{(c.motivos ?? []).join('; ')}</p>
              )}
            </div>
            {c.elegivel ? (
              <Botao
                disabled={escolher.isPending}
                onClick={() => {
                  escolher.mutate({ usuarioId: c.usuario?.id as number, forcar: false });
                }}
              >
                Escolher
              </Botao>
            ) : podeForcar ? (
              <Botao
                variante="secundario"
                onClick={() => {
                  definirForcando(c);
                }}
              >
                Escalar mesmo assim
              </Botao>
            ) : (
              <span className="texto-secundario">Impedido</span>
            )}
          </div>
        ))}
      </Modal>
      <DialogoConfirmacao
        aberto={forcando !== null}
        titulo="Escalar mesmo com impedimento?"
        mensagem={`${forcando?.usuario?.nome ?? ''} está impedido: ${(forcando?.motivos ?? []).join('; ')}. Mesmo assim, quer escalar?`}
        textoConfirmar="Escalar mesmo assim"
        perigoso
        onCancelar={() => {
          definirForcando(null);
        }}
        onConfirmar={() => {
          const id = forcando?.usuario?.id;
          definirForcando(null);
          if (id !== undefined) escolher.mutate({ usuarioId: id, forcar: true });
        }}
      />
    </>
  );
}

function EditarVaga({
  vaga,
  celebracaoId,
  onFechar,
  onSalvo,
}: {
  vaga: VagaEscalaDTO;
  celebracaoId: number;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const { mostrar } = useToast();
  const [quantidade, definirQuantidade] = useState(vaga.quantidade ? String(vaga.quantidade) : '');
  const [chegada, definirChegada] = useState(horaCurta(vaga.horarioChegada));
  const [observacao, definirObservacao] = useState(vaga.observacao ?? '');
  const salvar = useMutation({
    mutationFn: () =>
      putVagasPorId(vaga.vagaId as number, {
        celebracaoId,
        funcaoId: vaga.funcao?.id as number,
        quantidade: quantidade === '' ? undefined : Number(quantidade),
        horarioChegada: chegada === '' ? undefined : chegada,
        observacao: observacao.trim() === '' ? undefined : observacao.trim(),
      }),
    onSuccess: () => {
      mostrar('Vaga atualizada', 'sucesso');
      onSalvo();
    },
    onError: (e: unknown) => {
      mostrar(e instanceof ErroApi ? e.message : 'Não foi possível salvar agora', 'erro');
    },
  });
  return (
    <Modal titulo={`Editar ${vaga.funcao?.nome ?? 'vaga'}`} aberto onFechar={onFechar}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          salvar.mutate();
        }}
      >
        <Campo
          rotulo="Quantas pessoas"
          type="number"
          min={1}
          value={quantidade}
          onChange={(e) => {
            definirQuantidade(e.target.value);
          }}
        />
        <Campo
          rotulo="Chegar até"
          type="time"
          value={chegada}
          onChange={(e) => {
            definirChegada(e.target.value);
          }}
        />
        <Campo
          rotulo="Observação"
          value={observacao}
          onChange={(e) => {
            definirObservacao(e.target.value);
          }}
        />
        <Botao type="submit" disabled={salvar.isPending}>
          Salvar
        </Botao>
      </form>
    </Modal>
  );
}
