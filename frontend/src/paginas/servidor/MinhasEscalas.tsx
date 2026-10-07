import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getGetMeEscalasQueryKey,
  getMeEscalas,
  postAlocacoesPorIdResponder,
} from '../../api/generated/servio';
import type { EscalaPessoalDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { Botao } from '../../componentes/Botao';
import { Card } from '../../componentes/Card';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { SeloStatus, type StatusSelo } from '../../componentes/SeloStatus';
import { useToast } from '../../componentes/toastContexto';
import { useConexao } from '../../pwa/useConexao';
import {
  dataPorExtenso,
  horaCurta,
  hojeIso,
  prazoLegivel,
  somarDias,
} from '../../utilidades/datas';

type Aba = 'proximas' | 'passadas';

const DIAS_DE_JANELA = 365;

function periodo(aba: Aba): { de: string; ate: string } {
  const hoje = hojeIso();
  return aba === 'proximas'
    ? { de: hoje, ate: somarDias(hoje, DIAS_DE_JANELA) }
    : { de: somarDias(hoje, -DIAS_DE_JANELA), ate: somarDias(hoje, -1) };
}

const SELO_DO_STATUS: Record<string, StatusSelo> = {
  PENDENTE: 'pendente',
  ACEITA: 'confirmado',
  RECUSADA: 'recusado',
  EXPIRADA: 'expirado',
};

/** Escalas da pessoa logada: próximas primeiro; as passadas numa aba à parte. */
export function MinhasEscalas() {
  const [aba, definirAba] = useState<Aba>('proximas');
  const params = periodo(aba);
  const online = useConexao();
  const consulta = useQuery({
    queryKey: getGetMeEscalasQueryKey(params),
    queryFn: async () => (await getMeEscalas(params)).data,
  });

  return (
    <>
      <h1 className="titulo-pagina">Minhas escalas</h1>
      {!online ? (
        <p className="aviso" role="status">
          Você está sem conexão. Mostrando as últimas escalas salvas.
        </p>
      ) : null}
      <div className="abas" role="group" aria-label="Período">
        <Botao
          variante={aba === 'proximas' ? 'primario' : 'secundario'}
          aria-pressed={aba === 'proximas'}
          onClick={() => {
            definirAba('proximas');
          }}
        >
          Próximas
        </Botao>
        <Botao
          variante={aba === 'passadas' ? 'primario' : 'secundario'}
          aria-pressed={aba === 'passadas'}
          onClick={() => {
            definirAba('passadas');
          }}
        >
          Passadas
        </Botao>
      </div>

      {consulta.isPending ? <Esqueleto /> : null}
      {consulta.isError ? (
        <EstadoVazio
          titulo="Não foi possível carregar as escalas"
          texto="Verifique sua conexão e atualize a página."
        />
      ) : null}
      {consulta.data && consulta.data.length === 0 ? (
        <EstadoVazio
          titulo={aba === 'proximas' ? 'Você não tem escalas marcadas' : 'Nenhuma escala passada'}
          texto={
            aba === 'proximas'
              ? 'Quando a coordenação convidar você, a escala aparece aqui.'
              : undefined
          }
        />
      ) : null}
      {consulta.data?.map((escala) => (
        <EscalaCard key={escala.alocacaoId} escala={escala} mostrarResposta={aba === 'proximas'} />
      ))}
    </>
  );
}

function EscalaCard({
  escala,
  mostrarResposta,
}: {
  escala: EscalaPessoalDTO;
  mostrarResposta: boolean;
}) {
  const [recusando, definirRecusando] = useState(false);
  const [justificativa, definirJustificativa] = useState('');
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const responder = useMutation({
    mutationFn: (aceitar: boolean) =>
      postAlocacoesPorIdResponder(escala.alocacaoId as number, {
        aceitar,
        justificativa: aceitar ? undefined : justificativa.trim() || undefined,
      }),
    onSuccess: (_resposta, aceitar) => {
      mostrar(aceitar ? 'Presença confirmada. Obrigado!' : 'Recusa registrada', 'sucesso');
      void cliente.invalidateQueries({ queryKey: getGetMeEscalasQueryKey() });
    },
    onError: (erro: unknown) => {
      mostrar(erro instanceof ErroApi ? erro.message : 'Não foi possível responder agora', 'erro');
    },
  });

  const pendente = escala.status === 'PENDENTE';
  const celebracao = escala.celebracao;
  return (
    <Card>
      <div className="escala__cabecalho">
        <p className="escala__data">{dataPorExtenso(celebracao?.data)}</p>
        <SeloStatus status={SELO_DO_STATUS[escala.status ?? ''] ?? 'neutro'} />
      </div>
      <h2 className="escala__titulo">{celebracao?.titulo}</h2>
      <dl className="convite__dados">
        <dt>Horário da celebração</dt>
        <dd>{horaCurta(celebracao?.hora)}</dd>
        {escala.horarioChegada ? (
          <>
            <dt>Chegar até</dt>
            <dd className="convite__destaque">{horaCurta(escala.horarioChegada)}</dd>
          </>
        ) : null}
        <dt>Sua função</dt>
        <dd>{escala.funcao?.nome}</dd>
        <dt>Pastoral</dt>
        <dd>{escala.pastoral?.nome}</dd>
        {escala.observacao ? (
          <>
            <dt>Observação</dt>
            <dd>{escala.observacao}</dd>
          </>
        ) : null}
      </dl>

      {mostrarResposta && pendente ? (
        <div className="escala__resposta">
          <p className="texto-corpo">Responda até {prazoLegivel(escala.dataLimiteResposta)}.</p>
          {recusando ? (
            <>
              <label className="campo">
                <span className="campo__rotulo">Motivo (opcional)</span>
                <textarea
                  className="campo__input"
                  rows={3}
                  maxLength={500}
                  value={justificativa}
                  onChange={(e) => {
                    definirJustificativa(e.target.value);
                  }}
                />
              </label>
              <div className="acoes-modal">
                <Botao
                  variante="secundario"
                  onClick={() => {
                    definirRecusando(false);
                  }}
                >
                  Voltar
                </Botao>
                <Botao
                  variante="perigo"
                  disabled={responder.isPending}
                  onClick={() => {
                    responder.mutate(false);
                  }}
                >
                  Enviar recusa
                </Botao>
              </div>
            </>
          ) : (
            <div className="convite__botoes">
              <Botao
                className="convite__grande"
                disabled={responder.isPending}
                onClick={() => {
                  responder.mutate(true);
                }}
              >
                Confirmo minha presença
              </Botao>
              <Botao
                variante="secundario"
                className="convite__grande"
                disabled={responder.isPending}
                onClick={() => {
                  definirRecusando(true);
                }}
              >
                Não poderei ir
              </Botao>
            </div>
          )}
        </div>
      ) : null}
    </Card>
  );
}
