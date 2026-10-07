import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  getGetPastoraisPorPastoralIdFinanceiroQueryKey,
  getGetPastoraisPorPastoralIdFinanceiroSaldoQueryKey,
  getPastoraisPorPastoralIdFinanceiro,
  getPastoraisPorPastoralIdFinanceiroSaldo,
  postPastoraisPorPastoralIdFinanceiro,
} from '../../api/generated/servio';
import type { LancamentoFinanceiroResponseDTO } from '../../api/generated/modelos';
import { ErroApi } from '../../api/cliente';
import { useAcessoDaPastoral } from '../../auth/useAcesso';
import { Botao } from '../../componentes/Botao';
import { CardEstatistica } from '../../componentes/CardEstatistica';
import { Campo } from '../../componentes/Campo';
import { Card } from '../../componentes/Card';
import { EstadoVazio } from '../../componentes/EstadoVazio';
import { Esqueleto } from '../../componentes/Esqueleto';
import { Modal } from '../../componentes/Modal';
import { Tabela, type ColunaTabela } from '../../componentes/Tabela';
import { useToast } from '../../componentes/toastContexto';
import { dataPorExtenso, hojeIso } from '../../utilidades/datas';

const esquema = z.object({
  tipo: z.enum(['ENTRADA', 'SAIDA']),
  valor: z.coerce
    .number({ message: 'Informe o valor' })
    .positive('O valor precisa ser maior que zero'),
  descricao: z.string().min(1, 'Descreva o lançamento').max(200, 'Descrição muito longa'),
  dataLancamento: z.string().min(1, 'Informe a data'),
});
type Dados = z.infer<typeof esquema>;

const formatarReais = (valor: number | undefined) =>
  (valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

/** Financeiro: quem vê tem o saldo e o extrato; o tesoureiro lança entradas e saídas. */
export function Financeiro() {
  const { pastoralId, acesso } = useAcessoDaPastoral();
  const cliente = useQueryClient();
  const { mostrar } = useToast();
  const [lancando, definirLancando] = useState(false);
  const params = { page: 0, size: 50, sort: ['dataLancamento'] };
  const extrato = useQuery({
    queryKey: getGetPastoraisPorPastoralIdFinanceiroQueryKey(pastoralId, params),
    queryFn: async () => (await getPastoraisPorPastoralIdFinanceiro(pastoralId, params)).data,
  });
  const saldo = useQuery({
    queryKey: getGetPastoraisPorPastoralIdFinanceiroSaldoQueryKey(pastoralId),
    queryFn: async () => (await getPastoraisPorPastoralIdFinanceiroSaldo(pastoralId)).data,
  });

  const colunas: ColunaTabela<LancamentoFinanceiroResponseDTO>[] = [
    { chave: 'data', titulo: 'Data', celula: (l) => dataPorExtenso(l.dataLancamento) },
    { chave: 'descricao', titulo: 'Descrição', celula: (l) => l.descricao ?? '' },
    { chave: 'tipo', titulo: 'Tipo', celula: (l) => (l.tipo === 'ENTRADA' ? 'Entrada' : 'Saída') },
    { chave: 'valor', titulo: 'Valor', celula: (l) => formatarReais(l.valor) },
  ];

  if (!acesso.veFinanceiro) {
    return <EstadoVazio titulo="Esta área não está disponível para o seu acesso" />;
  }

  return (
    <>
      <div className="cabecalho-pagina">
        <h1 className="titulo-pagina">Financeiro</h1>
        {acesso.lancaFinanceiro ? (
          <Botao
            onClick={() => {
              definirLancando(true);
            }}
          >
            Novo lançamento
          </Botao>
        ) : null}
      </div>
      {saldo.data ? (
        <div className="grade-cards">
          <CardEstatistica rotulo="Entradas" valor={formatarReais(saldo.data.totalEntradas)} />
          <CardEstatistica rotulo="Saídas" valor={formatarReais(saldo.data.totalSaidas)} />
          <CardEstatistica rotulo="Saldo" valor={formatarReais(saldo.data.saldo)} />
        </div>
      ) : (
        <Esqueleto linhas={2} />
      )}
      <Card titulo="Lançamentos">
        {extrato.isPending ? <Esqueleto /> : null}
        {extrato.data && (extrato.data.content ?? []).length === 0 ? (
          <EstadoVazio titulo="Nenhum lançamento ainda" />
        ) : null}
        {extrato.data && (extrato.data.content ?? []).length > 0 ? (
          <Tabela
            titulo="Lançamentos"
            linhas={extrato.data.content ?? []}
            chaveDaLinha={(l) => l.id ?? 0}
            colunas={colunas}
          />
        ) : null}
      </Card>
      <NovoLancamento
        aberto={lancando}
        pastoralId={pastoralId}
        onFechar={() => {
          definirLancando(false);
        }}
        onSalvo={() => {
          definirLancando(false);
          mostrar('Lançamento salvo', 'sucesso');
          void cliente.invalidateQueries({
            queryKey: getGetPastoraisPorPastoralIdFinanceiroQueryKey(pastoralId),
          });
          void cliente.invalidateQueries({
            queryKey: getGetPastoraisPorPastoralIdFinanceiroSaldoQueryKey(pastoralId),
          });
        }}
      />
    </>
  );
}

function NovoLancamento({
  aberto,
  pastoralId,
  onFechar,
  onSalvo,
}: {
  aberto: boolean;
  pastoralId: number;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const { mostrar } = useToast();
  const formulario = useForm<z.input<typeof esquema>, unknown, Dados>({
    resolver: zodResolver(esquema),
    defaultValues: { tipo: 'ENTRADA', valor: Number.NaN, descricao: '', dataLancamento: hojeIso() },
  });
  const salvar = useMutation({
    mutationFn: (dados: Dados) =>
      postPastoraisPorPastoralIdFinanceiro(pastoralId, {
        tipo: dados.tipo,
        valor: dados.valor,
        descricao: dados.descricao.trim(),
        dataLancamento: dados.dataLancamento,
      }),
    onSuccess: () => {
      formulario.reset();
      onSalvo();
    },
    onError: (e: unknown) => {
      mostrar(e instanceof ErroApi ? e.message : 'Não foi possível salvar agora', 'erro');
    },
  });
  const enviar = formulario.handleSubmit((dados) => {
    salvar.mutate(dados);
  });
  return (
    <Modal titulo="Novo lançamento" aberto={aberto} onFechar={onFechar}>
      <form
        onSubmit={(e) => {
          void enviar(e);
        }}
        noValidate
      >
        <label className="campo">
          <span className="campo__rotulo">Tipo</span>
          <select {...formulario.register('tipo')}>
            <option value="ENTRADA">Entrada</option>
            <option value="SAIDA">Saída</option>
          </select>
        </label>
        <Campo
          rotulo="Valor (R$)"
          type="number"
          step="0.01"
          min="0"
          erro={formulario.formState.errors.valor?.message}
          {...formulario.register('valor')}
        />
        <Campo
          rotulo="Descrição"
          erro={formulario.formState.errors.descricao?.message}
          {...formulario.register('descricao')}
        />
        <Campo
          rotulo="Data"
          type="date"
          erro={formulario.formState.errors.dataLancamento?.message}
          {...formulario.register('dataLancamento')}
        />
        <Botao type="submit" disabled={salvar.isPending}>
          Salvar lançamento
        </Botao>
      </form>
    </Modal>
  );
}
