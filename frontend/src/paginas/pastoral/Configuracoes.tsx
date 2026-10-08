import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  getGetPastoraisPorPastoralIdConfigQueryKey,
  getGetPastoraisPorPastoralIdModelosVagaQueryKey,
  getGetRegrasCatalogoQueryKey,
  getPastoraisPorPastoralIdConfig,
  getPastoraisPorPastoralIdModelosVaga,
  getRegrasCatalogo,
  postPastoraisPorPastoralIdModelosVagaAplicarFuturas,
  putPastoraisPorPastoralIdConfigPorChave,
} from '../../api/generated/servio';
import type { PastoralConfigResponseDTO, RegraCatalogoDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { useAcessoDaPastoral } from '../../auth/useAcesso';
import { Botao } from '../../componentes/Botao';
import { Campo } from '../../componentes/Campo';
import { Card } from '../../componentes/Card';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { useToast } from '../../componentes/toastContexto';

type Valores = Record<string, unknown>;

const capitalizar = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);

/** Ajustes da pastoral que não são regras de elegibilidade (prazos, lembretes, cobertura). */
const AJUSTES: {
  chave: string;
  titulo: string;
  descricao: string;
  padrao: Valores;
  ativa?: boolean;
}[] = [
  {
    chave: 'PRAZO_RESPOSTA',
    titulo: 'Prazo para responder ao convite',
    descricao: 'Quantas horas a pessoa tem para confirmar ou recusar.',
    padrao: { horas: 24 },
  },
  {
    chave: 'LEMBRETE_RESPOSTA',
    titulo: 'Lembrete para quem não respondeu',
    descricao: 'Quantas horas antes do prazo o lembrete é enviado.',
    padrao: { horasAntesDoPrazo: 6 },
  },
  {
    chave: 'LEMBRETE_SERVICO',
    titulo: 'Lembrete antes do serviço',
    descricao: 'Quantas horas antes da celebração quem já confirmou recebe o lembrete.',
    padrao: { horasAntes: 24 },
  },
  {
    chave: 'COBERTURA_AUTOMATICA',
    titulo: 'Cobertura automática de vagas',
    descricao: 'Preencher sozinho as vagas dos modelos nas celebrações futuras.',
    padrao: {},
    ativa: false,
  },
];

const NOME_DO_PARAMETRO: Record<string, string> = {
  horas: 'Horas',
  horasAntesDoPrazo: 'Horas antes do prazo',
  horasAntes: 'Horas antes da celebração',
};

/**
 * Configurações (só coordenação). O formulário das regras sai do catálogo do backend
 * (GET /api/regras/catalogo): nenhuma regra é escrita aqui.
 */
export function Configuracoes() {
  const { pastoralId, acesso } = useAcessoDaPastoral();
  const cliente = useQueryClient();
  const catalogo = useQuery({
    queryKey: getGetRegrasCatalogoQueryKey(),
    queryFn: async () => (await getRegrasCatalogo()).data,
    enabled: acesso.coordena,
  });
  const configs = useQuery({
    queryKey: getGetPastoraisPorPastoralIdConfigQueryKey(pastoralId),
    queryFn: async () => (await getPastoraisPorPastoralIdConfig(pastoralId)).data,
    enabled: acesso.coordena,
  });

  if (!acesso.coordena) {
    return (
      <EstadoVazio
        titulo="Esta área é só para a coordenação"
        texto="Peça à coordenação para ajustar estas configurações."
      />
    );
  }

  const atualConfig = (chave: string): PastoralConfigResponseDTO | undefined =>
    (configs.data ?? []).find((c) => c.chave === chave);

  // O catálogo também traz PRAZO_RESPOSTA, que já tem campo próprio em "Prazos": sem repetir.
  const regrasDoCatalogo = (catalogo.data ?? []).filter(
    (regra) => !AJUSTES.some((ajuste) => ajuste.chave === regra.codigo),
  );

  return (
    <>
      <h1 className="titulo-pagina">Configurações</h1>
      {configs.isPending || catalogo.isPending ? <Esqueleto linhas={6} /> : null}

      <Card titulo="Prazos, lembretes e cobertura">
        {AJUSTES.map((ajuste) => (
          <ItemConfiguracao
            key={ajuste.chave}
            pastoralId={pastoralId}
            chave={ajuste.chave}
            titulo={ajuste.titulo}
            descricao={ajuste.descricao}
            padrao={ajuste.padrao}
            ativaPadrao={ajuste.ativa ?? true}
            atual={atualConfig(ajuste.chave)}
            aoSalvar={() => {
              void cliente.invalidateQueries({
                queryKey: getGetPastoraisPorPastoralIdConfigQueryKey(pastoralId),
              });
            }}
          />
        ))}
      </Card>

      <Card titulo="Quem pode servir">
        {regrasDoCatalogo.length === 0 && catalogo.isSuccess ? (
          <EstadoVazio titulo="Nenhuma regra disponível" />
        ) : null}
        {regrasDoCatalogo.map((regra: RegraCatalogoDTO) => (
          <ItemConfiguracao
            key={regra.codigo}
            pastoralId={pastoralId}
            chave={regra.codigo ?? ''}
            titulo={regra.descricao ?? regra.codigo ?? ''}
            descricao=""
            padrao={regra.padroes ?? {}}
            ativaPadrao
            atual={atualConfig(regra.codigo ?? '')}
            aoSalvar={() => {
              void cliente.invalidateQueries({
                queryKey: getGetPastoraisPorPastoralIdConfigQueryKey(pastoralId),
              });
            }}
          />
        ))}
      </Card>

      <Modelos pastoralId={pastoralId} />
      <p className="texto-secundario">
        Regras e prazos são lidos do servidor; o que você salva vale para a pastoral.
      </p>
      {catalogo.isError ? <EstadoVazio titulo="Não foi possível carregar as regras" /> : null}
    </>
  );
}

function ItemConfiguracao({
  pastoralId,
  chave,
  titulo,
  descricao,
  padrao,
  ativaPadrao,
  atual,
  aoSalvar,
}: {
  pastoralId: number;
  chave: string;
  titulo: string;
  descricao: string;
  padrao: Valores;
  ativaPadrao: boolean;
  atual: PastoralConfigResponseDTO | undefined;
  aoSalvar: () => void;
}) {
  const { mostrar } = useToast();
  const iniciais: Valores = { ...padrao, ...(atual?.parametros ?? {}) };
  const [ativa, definirAtiva] = useState<boolean>(atual?.ativa ?? ativaPadrao);
  const [valores, definirValores] = useState<Valores>(iniciais);
  const salvar = useMutation({
    mutationFn: () =>
      putPastoraisPorPastoralIdConfigPorChave(pastoralId, chave, {
        ativa,
        parametros: valores,
      }),
    onSuccess: () => {
      mostrar('Configuração salva', 'sucesso');
      aoSalvar();
    },
    onError: (e: unknown) => {
      mostrar(e instanceof ErroApi ? e.message : 'Não foi possível salvar agora', 'erro');
    },
  });

  return (
    <div className="config-item">
      <div className="config-item__cabecalho">
        <label className="config-item__ativa">
          <input
            type="checkbox"
            checked={ativa}
            onChange={(e) => {
              definirAtiva(e.target.checked);
            }}
          />
          <span>{titulo}</span>
        </label>
      </div>
      {descricao ? <p className="texto-secundario">{descricao}</p> : null}
      {Object.entries(valores).map(([nome, valor]) => {
        const rotulo = NOME_DO_PARAMETRO[nome] ?? capitalizar(nome);
        if (typeof valor === 'boolean') {
          return (
            <label key={nome} className="config-item__ativa">
              <input
                type="checkbox"
                checked={valor}
                onChange={(e) => {
                  definirValores({ ...valores, [nome]: e.target.checked });
                }}
              />
              <span>{rotulo}</span>
            </label>
          );
        }
        if (typeof valor === 'number') {
          return (
            <Campo
              key={nome}
              rotulo={rotulo}
              type="number"
              min={0}
              value={String(valor)}
              onChange={(e) => {
                definirValores({ ...valores, [nome]: Number(e.target.value) });
              }}
            />
          );
        }
        return null;
      })}
      <Botao
        disabled={salvar.isPending}
        onClick={() => {
          salvar.mutate();
        }}
      >
        Salvar
      </Botao>
    </div>
  );
}

function Modelos({ pastoralId }: { pastoralId: number }) {
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const modelos = useQuery({
    queryKey: getGetPastoraisPorPastoralIdModelosVagaQueryKey(pastoralId),
    queryFn: async () => (await getPastoraisPorPastoralIdModelosVaga(pastoralId)).data,
  });
  const aplicar = useMutation({
    mutationFn: () => postPastoraisPorPastoralIdModelosVagaAplicarFuturas(pastoralId),
    onSuccess: () => {
      mostrar('Modelos aplicados às celebrações futuras', 'sucesso');
      void cliente.invalidateQueries({
        queryKey: getGetPastoraisPorPastoralIdModelosVagaQueryKey(pastoralId),
      });
    },
    onError: (e: unknown) => {
      mostrar(e instanceof ErroApi ? e.message : 'Não foi possível aplicar agora', 'erro');
    },
  });
  return (
    <Card titulo="Modelos de vaga">
      {(modelos.data ?? []).length === 0 ? (
        <EstadoVazio titulo="Nenhum modelo de vaga ainda" />
      ) : (
        <ul className="lista-simples">
          {(modelos.data ?? []).map((m) => (
            <li key={m.id}>
              {m.tipoCelebracao === 'EVENTO' ? 'Evento' : 'Missa dominical'}:{' '}
              {String(m.quantidade ?? 0)} pessoa(s)
            </li>
          ))}
        </ul>
      )}
      <Botao
        variante="secundario"
        disabled={aplicar.isPending}
        onClick={() => {
          aplicar.mutate();
        }}
      >
        Aplicar às celebrações futuras
      </Botao>
    </Card>
  );
}
