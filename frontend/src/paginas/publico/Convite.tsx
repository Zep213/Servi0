import { useEffect, useState, type ReactNode } from 'react';
import { postConfirmacoesDetalhes, postConfirmacoesResponder } from '../../api/generated/servio';
import type { ConfirmacaoDetalhesDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { Botao } from '../../componentes/Botao';
import { Card } from '../../componentes/Card';
import { Esqueleto } from '../../componentes/Esqueleto';
import { dataPorExtenso, horaCurta, prazoLegivel, primeiroNome } from '../../utilidades/datas';

/**
 * Convite pelo link do e-mail (/convite#token). Sem login. O token sai do fragmento e a URL é
 * limpa logo em seguida, para não ficar na barra nem no histórico.
 */

type Estado =
  | { tipo: 'carregando' }
  | { tipo: 'pronto'; detalhes: ConfirmacaoDetalhesDTO }
  | { tipo: 'respondido'; aceitou: boolean }
  | { tipo: 'expirado' }
  | { tipo: 'indisponivel' }
  | { tipo: 'limite' }
  | { tipo: 'erro' };

/** Lê o token do fragmento e apaga o fragmento da barra de endereço. */
function lerTokenDoFragmento(): string | null {
  const token = window.location.hash.replace(/^#/, '');
  if (!token) return null;
  window.history.replaceState(null, '', window.location.pathname);
  return token;
}

const MENSAGEM_INDISPONIVEL =
  'Este convite não está mais disponível. Se você já respondeu, não precisa fazer nada.';

export function Convite() {
  const [token] = useState<string | null>(() => lerTokenDoFragmento());
  const [estado, definirEstado] = useState<Estado>(() =>
    token ? { tipo: 'carregando' } : { tipo: 'indisponivel' },
  );
  const [mostrarRecusa, definirMostrarRecusa] = useState(false);
  const [justificativa, definirJustificativa] = useState('');
  const [enviando, definirEnviando] = useState(false);

  useEffect(() => {
    if (!token) return;
    let cancelado = false;
    postConfirmacoesDetalhes({ token }).then(
      (resposta) => {
        if (!cancelado) definirEstado({ tipo: 'pronto', detalhes: resposta.data });
      },
      (erro: unknown) => {
        if (!cancelado) definirEstado(estadoDeErro(erro));
      },
    );
    return () => {
      cancelado = true;
    };
  }, [token]);

  async function responder(aceitar: boolean) {
    if (!token) return;
    definirEnviando(true);
    try {
      const resposta = await postConfirmacoesResponder({
        token,
        aceitar,
        justificativa: aceitar ? undefined : justificativa.trim() || undefined,
      });
      definirEstado({ tipo: 'respondido', aceitou: resposta.data.status === 'ACEITA' });
    } catch (erro) {
      definirEstado(estadoDeErro(erro));
    } finally {
      definirEnviando(false);
    }
  }

  return (
    <main className="convite">
      <h1 className="convite__marca">Servio</h1>
      {renderizar()}
    </main>
  );

  function renderizar() {
    switch (estado.tipo) {
      case 'carregando':
        return <Esqueleto linhas={5} />;
      case 'indisponivel':
        return <Aviso titulo="Convite não encontrado" texto={MENSAGEM_INDISPONIVEL} />;
      case 'expirado':
        return (
          <Aviso
            titulo="O prazo para responder terminou"
            texto="Por favor, fale com a coordenação da sua pastoral. Ela pode escolher outra pessoa."
          />
        );
      case 'limite':
        return <Aviso titulo="Muitas tentativas" texto="Aguarde alguns minutos e tente de novo." />;
      case 'erro':
        return (
          <Aviso
            titulo="Não foi possível carregar o convite"
            texto="Verifique sua conexão e tente de novo."
            acao={
              <Botao
                onClick={() => {
                  if (token) {
                    definirEstado({ tipo: 'carregando' });
                    void postConfirmacoesDetalhes({ token }).then(
                      (resposta) => {
                        definirEstado({ tipo: 'pronto', detalhes: resposta.data });
                      },
                      (erro: unknown) => {
                        definirEstado(estadoDeErro(erro));
                      },
                    );
                  }
                }}
              >
                Tentar de novo
              </Botao>
            }
          />
        );
      case 'respondido':
        return estado.aceitou ? (
          <Aviso
            titulo="Presença confirmada. Obrigado!"
            texto="Você receberá um lembrete antes da celebração."
          />
        ) : (
          <Aviso
            titulo="Recusa registrada"
            texto="A coordenação foi avisada e vai organizar a escala."
          />
        );
      case 'pronto':
        return (
          <Detalhes
            detalhes={estado.detalhes}
            mostrarRecusa={mostrarRecusa}
            justificativa={justificativa}
            enviando={enviando}
            onMostrarRecusa={definirMostrarRecusa}
            onJustificativa={definirJustificativa}
            onResponder={(aceitar) => {
              void responder(aceitar);
            }}
          />
        );
    }
  }
}

interface DetalhesProps {
  detalhes: ConfirmacaoDetalhesDTO;
  mostrarRecusa: boolean;
  justificativa: string;
  enviando: boolean;
  onMostrarRecusa: (valor: boolean) => void;
  onJustificativa: (valor: string) => void;
  onResponder: (aceitar: boolean) => void;
}

function Detalhes({
  detalhes,
  mostrarRecusa,
  justificativa,
  enviando,
  onMostrarRecusa,
  onJustificativa,
  onResponder,
}: DetalhesProps) {
  return (
    <Card>
      <h2 className="convite__saudacao">Olá, {primeiroNome(detalhes.primeiroNome)}!</h2>
      <p className="convite__pedido">Você foi convidado(a) para servir:</p>
      <dl className="convite__dados">
        <dt>Celebração</dt>
        <dd>{detalhes.celebracaoTitulo}</dd>
        <dt>Data</dt>
        <dd>{dataPorExtenso(detalhes.data)}</dd>
        <dt>Horário da celebração</dt>
        <dd>{horaCurta(detalhes.hora)}</dd>
        {detalhes.horarioChegada ? (
          <>
            <dt>Chegar até</dt>
            <dd className="convite__destaque">{horaCurta(detalhes.horarioChegada)}</dd>
          </>
        ) : null}
        <dt>Sua função</dt>
        <dd>{detalhes.funcao}</dd>
        {detalhes.observacao ? (
          <>
            <dt>Observação</dt>
            <dd>{detalhes.observacao}</dd>
          </>
        ) : null}
      </dl>
      <p className="texto-corpo">Responda até {prazoLegivel(detalhes.prazo)}.</p>

      {mostrarRecusa ? (
        <div className="convite__recusa">
          <label className="campo">
            <span className="campo__rotulo">Motivo (opcional)</span>
            <textarea
              className="campo__input"
              rows={3}
              maxLength={500}
              value={justificativa}
              onChange={(e) => {
                onJustificativa(e.target.value);
              }}
            />
          </label>
          <div className="acoes-modal">
            <Botao
              variante="secundario"
              onClick={() => {
                onMostrarRecusa(false);
              }}
            >
              Voltar
            </Botao>
            <Botao
              variante="perigo"
              disabled={enviando}
              onClick={() => {
                onResponder(false);
              }}
            >
              Enviar recusa
            </Botao>
          </div>
        </div>
      ) : (
        <div className="convite__botoes">
          <Botao
            className="convite__grande"
            disabled={enviando}
            onClick={() => {
              onResponder(true);
            }}
          >
            Confirmo minha presença
          </Botao>
          <Botao
            variante="secundario"
            className="convite__grande"
            disabled={enviando}
            onClick={() => {
              onMostrarRecusa(true);
            }}
          >
            Não poderei ir
          </Botao>
        </div>
      )}
    </Card>
  );
}

function Aviso({ titulo, texto, acao }: { titulo: string; texto: string; acao?: ReactNode }) {
  return (
    <Card>
      <h2 className="convite__saudacao">{titulo}</h2>
      <p className="texto-corpo">{texto}</p>
      {acao}
    </Card>
  );
}

function estadoDeErro(erro: unknown): Estado {
  if (erro instanceof ErroApi) {
    if (erro.status === 410) return { tipo: 'expirado' };
    if (erro.status === 404) return { tipo: 'indisponivel' };
    if (erro.status === 429) return { tipo: 'limite' };
  }
  return { tipo: 'erro' };
}
