import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { servidor } from '../../test/servidor';
import { renderizarRotas } from '../../test/utilidades';

function eu(papel: string, perfil = 'SERVIDOR') {
  return {
    id: 10,
    nome: 'Maria Souza',
    email: 'maria@exemplo.com',
    perfil,
    paroquiaId: 1,
    pastorais: [{ id: 1, nome: 'Pascom', papel }],
  };
}

const painel = {
  cards: { vagasTotais: 5, vagasOcupadas: 2, convitesPendentes: 1, alteracoesPendentes: 1 },
  celebracoes: [
    {
      id: 1,
      data: '2031-03-02',
      hora: '19:00:00',
      titulo: 'Missa dominical',
      tipo: 'MISSA_DOMINICAL',
      vagasTotais: 3,
      ocupadas: 1,
      confirmadas: 0,
      status: 'PENDENTE',
      aguardandoQuantidade: false,
    },
    {
      id: 2,
      data: '2031-03-09',
      hora: '20:00:00',
      titulo: 'Festa da padroeira',
      tipo: 'EVENTO',
      vagasTotais: 2,
      ocupadas: 0,
      confirmadas: 0,
      status: 'NAO_INICIADO',
      aguardandoQuantidade: false,
    },
    {
      id: 3,
      data: '2031-03-16',
      hora: '08:00:00',
      titulo: 'Missa da quaresma',
      tipo: 'MISSA_DOMINICAL',
      vagasTotais: 0,
      ocupadas: 0,
      confirmadas: 0,
      status: 'NAO_INICIADO',
      aguardandoQuantidade: true,
    },
  ],
  pendencias: {
    convitesVencendo: [],
    recusasSemSubstituto: [],
    alteracoesAguardando: [
      { id: 9, alocacaoId: 5, funcao: 'Leitor', usuarioAnterior: 'Ana', usuarioNovo: 'Bruno' },
    ],
  },
};

const escala = {
  celebracaoId: 1,
  vagas: [
    {
      vagaId: 11,
      funcao: { id: 3, nome: 'Comunicação' },
      quantidade: 2,
      alocacoes: [
        {
          id: 50,
          usuario: { id: 20, nome: 'Ana' },
          status: 'PENDENTE',
          origem: 'SORTEIO',
          dataLimiteResposta: '2031-03-01T18:00:00',
        },
      ],
    },
  ],
};

const candidatos = [
  { usuario: { id: 30, nome: 'Bruno' }, elegivel: true, motivos: [] },
  { usuario: { id: 31, nome: 'Carla' }, elegivel: false, motivos: ['Indisponível no dia'] },
];

function handlersBase(papel: string, perfil = 'SERVIDOR') {
  return [
    http.get('/api/me', () => HttpResponse.json(eu(papel, perfil))),
    http.get('/api/pastorais/:id/painel', () => HttpResponse.json(painel)),
    http.get('/api/pastorais/:id/celebracoes/:cid/escala', () => HttpResponse.json(escala)),
    http.get('/api/celebracoes/:id', () =>
      HttpResponse.json({
        id: 1,
        titulo: 'Missa dominical',
        tipo: 'MISSA_DOMINICAL',
        data: '2031-03-02',
        hora: '19:00:00',
      }),
    ),
    http.get('/api/vagas/:id/candidatos', () => HttpResponse.json(candidatos)),
  ];
}

describe('Painel do coordenador', () => {
  it('mostra os cartões, "Sortear" só nas missas e "Escalar" só nos eventos', async () => {
    servidor.use(...handlersBase('COORDENADOR'));
    renderizarRotas('/pastoral/1/painel');

    expect(await screen.findByText('Vagas no mês')).toBeInTheDocument();
    const linhaMissa = (await screen.findByText('Missa dominical')).closest('tr') as HTMLElement;
    const linhaFesta = screen.getByText('Festa da padroeira').closest('tr') as HTMLElement;
    expect(within(linhaMissa).getByRole('button', { name: 'Sortear' })).toBeInTheDocument();
    expect(within(linhaMissa).queryByRole('link', { name: 'Escalar' })).not.toBeInTheDocument();
    expect(within(linhaFesta).queryByRole('button', { name: 'Sortear' })).not.toBeInTheDocument();
    expect(within(linhaFesta).getByRole('link', { name: 'Escalar' })).toHaveAttribute(
      'href',
      '/pastoral/1/celebracoes/2',
    );
  });

  it('sem quantidade definida, a ação é "Definir vagas", sem sortear', async () => {
    servidor.use(...handlersBase('COORDENADOR'));
    renderizarRotas('/pastoral/1/painel');

    const linha = (await screen.findByText('Missa da quaresma')).closest('tr') as HTMLElement;
    expect(within(linha).getByRole('link', { name: 'Definir vagas' })).toHaveAttribute(
      'href',
      '/pastoral/1/celebracoes/3',
    );
    expect(within(linha).queryByRole('button', { name: 'Sortear' })).not.toBeInTheDocument();
  });

  it('aprovar a alteração do vice só aparece para coordenação', async () => {
    servidor.use(...handlersBase('VICE'));
    renderizarRotas('/pastoral/1/painel');
    expect(await screen.findByText('Bruno')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Aprovar' })).not.toBeInTheDocument();
  });

  it('coordenador vê Aprovar e Desfazer', async () => {
    servidor.use(...handlersBase('COORDENADOR'));
    renderizarRotas('/pastoral/1/painel');
    expect(await screen.findByRole('button', { name: 'Aprovar' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Desfazer' })).toBeInTheDocument();
  });
});

describe('Escala da celebração', () => {
  it('vice não vê "Escalar mesmo assim" e a pessoa impedida fica sem o botão', async () => {
    servidor.use(...handlersBase('VICE'));
    renderizarRotas('/pastoral/1/celebracoes/1');
    await userEvent.click(await screen.findByRole('button', { name: 'Escolher pessoa' }));

    const dialogo = await screen.findByRole('dialog', { name: 'Escolher pessoa' });
    expect(within(dialogo).getByText('Indisponível no dia')).toBeInTheDocument();
    expect(
      within(dialogo).queryByRole('button', { name: 'Escalar mesmo assim' }),
    ).not.toBeInTheDocument();
    expect(within(dialogo).getByRole('button', { name: 'Escolher' })).toBeInTheDocument();
  });

  it('coordenador força com confirmação que repete o motivo', async () => {
    let corpo: unknown = null;
    servidor.use(
      ...handlersBase('COORDENADOR'),
      http.post('/api/vagas/:id/escalar', async ({ request }) => {
        corpo = await request.json();
        return HttpResponse.json({ id: 99 }, { status: 201 });
      }),
    );
    renderizarRotas('/pastoral/1/celebracoes/1');
    await userEvent.click(await screen.findByRole('button', { name: 'Escolher pessoa' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Escolher pessoa' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Escalar mesmo assim' }));

    const confirmacao = await screen.findByRole('dialog', {
      name: 'Escalar mesmo com impedimento?',
    });
    expect(within(confirmacao).getByText(/Indisponível no dia/)).toBeInTheDocument();
    await userEvent.click(within(confirmacao).getByRole('button', { name: 'Escalar mesmo assim' }));

    await waitFor(() => {
      expect(corpo).toMatchObject({ usuarioId: 31, forcar: true });
    });
  });
});

describe('Membros', () => {
  const membros = [
    {
      usuarioPastoralId: 5,
      usuario: { id: 20, nome: 'Ana' },
      papel: 'MEMBRO',
      email: 'ana@exemplo.com',
    },
  ];

  it('vice não vê o e-mail dos membros; coordenador vê', async () => {
    servidor.use(
      ...handlersBase('VICE'),
      http.get('/api/pastorais/:id/membros', () => HttpResponse.json(membros)),
    );
    renderizarRotas('/pastoral/1/membros');
    expect(await screen.findByText('Ana')).toBeInTheDocument();
    expect(screen.queryByText('ana@exemplo.com')).not.toBeInTheDocument();
  });

  it('coordenador vê o e-mail e pode remover, depois de confirmar', async () => {
    let removido: string | null = null;
    servidor.use(
      ...handlersBase('COORDENADOR'),
      http.get('/api/pastorais/:id/membros', () => HttpResponse.json(membros)),
      http.delete('/api/usuarios-pastorais/:id', ({ params }) => {
        removido = String(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderizarRotas('/pastoral/1/membros');
    expect(await screen.findByText('ana@exemplo.com')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Remover' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Remover esta pessoa da pastoral?' });
    expect(removido).toBeNull();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Remover' }));
    await waitFor(() => {
      expect(removido).toBe('5');
    });
  });

  async function abrirCriarConta() {
    renderizarRotas('/pastoral/1/membros');
    await userEvent.click(await screen.findByRole('button', { name: 'Adicionar pessoa' }));
    const busca = await screen.findByRole('dialog', { name: 'Adicionar pessoa' });
    await userEvent.click(within(busca).getByRole('button', { name: 'Criar conta nova' }));
    return screen.findByRole('dialog', { name: 'Criar conta nova' });
  }

  it('coordenador cria conta de SERVIDOR e já a coloca na pastoral com o papel escolhido', async () => {
    let contaCriada: unknown = null;
    let vinculo: unknown = null;
    servidor.use(
      ...handlersBase('COORDENADOR'),
      http.get('/api/pastorais/:id/membros', () => HttpResponse.json(membros)),
      http.post('/api/usuarios', async ({ request }) => {
        contaCriada = await request.json();
        return HttpResponse.json({ id: 77, nome: 'João Lima' }, { status: 201 });
      }),
      http.post('/api/usuarios-pastorais', async ({ request }) => {
        vinculo = await request.json();
        return HttpResponse.json({ id: 8 }, { status: 201 });
      }),
    );
    const dialogo = await abrirCriarConta();

    await userEvent.type(within(dialogo).getByLabelText('Nome completo'), 'João Lima');
    await userEvent.type(within(dialogo).getByLabelText('E-mail'), 'joao@exemplo.com');
    await userEvent.type(within(dialogo).getByLabelText('Senha inicial'), 'senha-forte-1');
    await userEvent.selectOptions(within(dialogo).getByLabelText('Papel'), 'SECRETARIO');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Criar conta e adicionar' }));

    expect(await screen.findByText('Conta criada e pessoa adicionada')).toBeInTheDocument();
    expect(contaCriada).toEqual({
      nome: 'João Lima',
      email: 'joao@exemplo.com',
      senha: 'senha-forte-1',
      perfil: 'SERVIDOR',
    });
    expect(vinculo).toEqual({ usuarioId: 77, pastoralId: 1, papel: 'SECRETARIO' });
  });

  it('senha curta não chega ao servidor', async () => {
    let chamou = false;
    servidor.use(
      ...handlersBase('COORDENADOR'),
      http.get('/api/pastorais/:id/membros', () => HttpResponse.json(membros)),
      http.post('/api/usuarios', () => {
        chamou = true;
        return HttpResponse.json({ id: 1 }, { status: 201 });
      }),
    );
    const dialogo = await abrirCriarConta();

    await userEvent.type(within(dialogo).getByLabelText('Nome completo'), 'João');
    await userEvent.type(within(dialogo).getByLabelText('E-mail'), 'joao@exemplo.com');
    await userEvent.type(within(dialogo).getByLabelText('Senha inicial'), '1234');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Criar conta e adicionar' }));

    expect(
      await within(dialogo).findByText('A senha precisa de pelo menos 8 caracteres'),
    ).toBeInTheDocument();
    expect(chamou).toBe(false);
  });

  it('e-mail já usado (409): mostra a mensagem do backend no campo de e-mail', async () => {
    servidor.use(
      ...handlersBase('COORDENADOR'),
      http.get('/api/pastorais/:id/membros', () => HttpResponse.json(membros)),
      http.post('/api/usuarios', () =>
        HttpResponse.json(
          { status: 409, detail: 'Já existe um usuário ativo com este e-mail' },
          { status: 409 },
        ),
      ),
    );
    const dialogo = await abrirCriarConta();

    await userEvent.type(within(dialogo).getByLabelText('Nome completo'), 'Ana');
    await userEvent.type(within(dialogo).getByLabelText('E-mail'), 'ana@exemplo.com');
    await userEvent.type(within(dialogo).getByLabelText('Senha inicial'), 'senha-forte-1');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Criar conta e adicionar' }));

    expect(
      await within(dialogo).findByText('Já existe um usuário ativo com este e-mail'),
    ).toBeInTheDocument();
  });
});

describe('Acesso às telas de gestão', () => {
  it('membro comum não entra na gestão da pastoral', async () => {
    servidor.use(...handlersBase('MEMBRO'));
    renderizarRotas('/pastoral/1/membros');
    expect(
      await screen.findByText('Esta área não está disponível para o seu acesso'),
    ).toBeInTheDocument();
  });

  it('financeiro: quem não lê vê a tela de acesso; o tesoureiro vê o botão de lançar', async () => {
    servidor.use(
      ...handlersBase('MEMBRO'),
      http.get('/api/pastorais/:id/financeiro', () => HttpResponse.json({ content: [] })),
      http.get('/api/pastorais/:id/financeiro/saldo', () =>
        HttpResponse.json({ totalEntradas: 0, totalSaidas: 0, saldo: 0 }),
      ),
    );
    renderizarRotas('/pastoral/1/financeiro');
    expect(
      await screen.findByText('Esta área não está disponível para o seu acesso'),
    ).toBeInTheDocument();
  });

  it('tesoureiro lança, coordenador só vê', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu('TESOUREIRO'))),
      http.get('/api/pastorais/:id/financeiro', () => HttpResponse.json({ content: [] })),
      http.get('/api/pastorais/:id/financeiro/saldo', () =>
        HttpResponse.json({ totalEntradas: 10, totalSaidas: 4, saldo: 6 }),
      ),
    );
    renderizarRotas('/pastoral/1/financeiro');
    expect(await screen.findByRole('button', { name: 'Novo lançamento' })).toBeInTheDocument();
  });

  it('coordenador não vê o botão de lançar', async () => {
    servidor.use(
      ...handlersBase('COORDENADOR'),
      http.get('/api/pastorais/:id/financeiro', () => HttpResponse.json({ content: [] })),
      http.get('/api/pastorais/:id/financeiro/saldo', () =>
        HttpResponse.json({ totalEntradas: 0, totalSaidas: 0, saldo: 0 }),
      ),
    );
    renderizarRotas('/pastoral/1/financeiro');
    await screen.findByText('Nenhum lançamento ainda');
    expect(screen.queryByRole('button', { name: 'Novo lançamento' })).not.toBeInTheDocument();
  });

  it('reuniões: membro pede, coordenador marca', async () => {
    servidor.use(
      ...handlersBase('MEMBRO'),
      http.get('/api/pastorais/:id/reunioes', () => HttpResponse.json({ content: [] })),
    );
    renderizarRotas('/pastoral/1/reunioes');
    expect(await screen.findByRole('button', { name: 'Pedir reunião' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Marcar reunião' })).not.toBeInTheDocument();
  });
});

describe('Configurações', () => {
  it('quem não coordena não vê o formulário', async () => {
    servidor.use(...handlersBase('VICE'));
    renderizarRotas('/pastoral/1/configuracoes');
    expect(await screen.findByText('Esta área é só para a coordenação')).toBeInTheDocument();
  });

  it('o formulário sai do catálogo e salva só o que mudou para a regra', async () => {
    let corpo: unknown = null;
    let chave: string | null = null;
    servidor.use(
      ...handlersBase('COORDENADOR'),
      http.get('/api/regras/catalogo', () =>
        HttpResponse.json([
          {
            codigo: 'INTERVALO_MINIMO',
            descricao: 'Intervalo mínimo entre escalas',
            padroes: { dias: 7 },
          },
        ]),
      ),
      http.get('/api/pastorais/:id/config', () => HttpResponse.json([])),
      http.get('/api/pastorais/:id/modelos-vaga', () => HttpResponse.json([])),
      http.put('/api/pastorais/:id/config/:chave', async ({ request, params }) => {
        corpo = await request.json();
        chave = String(params.chave);
        return HttpResponse.json({});
      }),
    );
    renderizarRotas('/pastoral/1/configuracoes');
    expect(await screen.findByText('Intervalo mínimo entre escalas')).toBeInTheDocument();
    const dias = screen.getByLabelText<HTMLInputElement>('Dias');
    await userEvent.clear(dias);
    await userEvent.type(dias, '14');
    const bloco = dias.closest('.config-item') as HTMLElement;
    await userEvent.click(within(bloco).getByRole('button', { name: 'Salvar' }));

    await waitFor(() => {
      expect(chave).toBe('INTERVALO_MINIMO');
      expect(corpo).toMatchObject({ ativa: true, parametros: { dias: 14 } });
    });
  });
});
