import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { servidor } from '../../test/servidor';
import { renderizarRotas } from '../../test/utilidades';

const padre = {
  id: 1,
  nome: 'Padre João',
  email: 'padre@exemplo.com',
  perfil: 'PADRE',
  paroquiaId: 1,
  pastorais: [],
};

const comunidade = { id: 5, nome: 'Matriz' };

const celebracao = {
  id: 1,
  comunidadeId: 5,
  data: '2031-03-02',
  hora: '19:00:00',
  tipoData: 'NORMAL',
  tipo: 'MISSA_DOMINICAL',
  titulo: 'Missa dominical',
};

const pastoral = { id: 2, nome: 'Pascom' };
const funcao = { id: 3, nome: 'Comunicação', pastoralId: 2 };

describe('Celebrações (área do padre)', () => {
  it('sem o perfil certo, vê a tela de acesso', async () => {
    servidor.use(http.get('/api/me', () => HttpResponse.json({ ...padre, perfil: 'SERVIDOR' })));
    renderizarRotas('/celebracoes');
    expect(
      await screen.findByText('Esta área não está disponível para o seu acesso'),
    ).toBeInTheDocument();
  });

  it('lista as celebrações do mês no calendário e na tabela', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(padre)),
      http.get('/api/celebracoes', () => HttpResponse.json({ content: [celebracao] })),
      http.get('/api/comunidades', () => HttpResponse.json({ content: [comunidade] })),
    );
    renderizarRotas('/celebracoes');

    expect(await screen.findAllByText('Missa dominical')).not.toHaveLength(0);
    expect(screen.getByText('Matriz')).toBeInTheDocument();
  });

  it('criar uma celebração manda os dados certos e avisa', async () => {
    let corpo: unknown = null;
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(padre)),
      http.get('/api/celebracoes', () => HttpResponse.json({ content: [] })),
      http.get('/api/comunidades', () => HttpResponse.json({ content: [comunidade] })),
      http.post('/api/celebracoes', async ({ request }) => {
        corpo = await request.json();
        return HttpResponse.json({ ...celebracao, id: 9 }, { status: 201 });
      }),
    );
    renderizarRotas('/celebracoes');

    await userEvent.click(await screen.findByRole('button', { name: 'Nova celebração' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Nova celebração' });
    await userEvent.selectOptions(within(dialogo).getByLabelText('Comunidade'), 'Matriz');
    await userEvent.type(within(dialogo).getByLabelText('Data'), '2031-04-01');
    await userEvent.type(within(dialogo).getByLabelText('Hora'), '18:00');
    await userEvent.type(within(dialogo).getByLabelText('Título (opcional)'), 'Festa junina');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Criar celebração' }));

    expect(await screen.findByText('Celebração criada')).toBeInTheDocument();
    await waitFor(() => {
      expect(corpo).toMatchObject({
        comunidadeId: 5,
        data: '2031-04-01',
        hora: '18:00',
        titulo: 'Festa junina',
      });
    });
  });
});

describe('Detalhe da celebração (área do padre)', () => {
  function handlersBase() {
    return [
      http.get('/api/me', () => HttpResponse.json(padre)),
      http.get('/api/celebracoes/:id', () => HttpResponse.json(celebracao)),
      http.get('/api/comunidades', () => HttpResponse.json({ content: [comunidade] })),
      http.get('/api/pastorais', () => HttpResponse.json({ content: [pastoral] })),
      http.get('/api/funcoes', () => HttpResponse.json({ content: [funcao] })),
    ];
  }

  it('mostra os dados da celebração', async () => {
    servidor.use(...handlersBase());
    renderizarRotas('/celebracoes/1');
    expect(await screen.findByText('Matriz')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Missa dominical' })).toBeInTheDocument();
  });

  it('responsabilizar pastoral escolhe a pastoral e a função e cria a vaga sem quantidade', async () => {
    let corpo: unknown = null;
    servidor.use(
      ...handlersBase(),
      http.post('/api/vagas', async ({ request }) => {
        corpo = await request.json();
        return HttpResponse.json({ id: 40 }, { status: 201 });
      }),
    );
    renderizarRotas('/celebracoes/1');

    const selectPastoral = await screen.findByLabelText('Pastoral');
    await userEvent.selectOptions(
      selectPastoral,
      await within(selectPastoral).findByRole('option', { name: 'Pascom' }),
    );
    const selectFuncao = screen.getByLabelText('Função');
    await userEvent.selectOptions(
      selectFuncao,
      await within(selectFuncao).findByRole('option', { name: 'Comunicação' }),
    );
    await userEvent.click(screen.getByRole('button', { name: 'Responsabilizar' }));

    expect(
      await screen.findByText(
        'Pastoral responsabilizada. A coordenação dela já pode definir a quantidade e escalar.',
      ),
    ).toBeInTheDocument();
    await waitFor(() => {
      expect(corpo).toMatchObject({ celebracaoId: 1, funcaoId: 3 });
      expect(corpo).not.toHaveProperty('quantidade');
    });
  });

  it('editar salva os campos alterados', async () => {
    let corpo: unknown = null;
    servidor.use(
      ...handlersBase(),
      http.put('/api/celebracoes/:id', async ({ request }) => {
        corpo = await request.json();
        return HttpResponse.json(celebracao);
      }),
    );
    renderizarRotas('/celebracoes/1');

    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Editar celebração' });
    const campoTitulo = within(dialogo).getByLabelText('Título (opcional)');
    await userEvent.clear(campoTitulo);
    await userEvent.type(campoTitulo, 'Missa das famílias');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Salvar' }));

    expect(await screen.findByText('Celebração atualizada')).toBeInTheDocument();
    await waitFor(() => {
      expect(corpo).toMatchObject({ titulo: 'Missa das famílias' });
    });
  });

  it('excluir pede confirmação e manda de volta à lista', async () => {
    let excluiu = false;
    servidor.use(
      ...handlersBase(),
      http.delete('/api/celebracoes/:id', () => {
        excluiu = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.get('/api/celebracoes', () => HttpResponse.json({ content: [] })),
    );
    renderizarRotas('/celebracoes/1');

    await userEvent.click(await screen.findByRole('button', { name: 'Excluir' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Excluir esta celebração?' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Excluir' }));

    await waitFor(() => {
      expect(excluiu).toBe(true);
    });
    expect(await screen.findByRole('heading', { name: 'Celebrações' })).toBeInTheDocument();
  });
});

describe('Pastorais (área do padre)', () => {
  it('lista as pastorais e cria uma nova', async () => {
    let corpo: unknown = null;
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(padre)),
      http.get('/api/pastorais', () => HttpResponse.json({ content: [pastoral] })),
      http.post('/api/pastorais', async ({ request }) => {
        corpo = await request.json();
        return HttpResponse.json({ id: 8, nome: 'ECC' }, { status: 201 });
      }),
    );
    renderizarRotas('/pastorais');

    expect(await screen.findByText('Pascom')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Membros' })).toHaveAttribute(
      'href',
      '/pastoral/2/membros',
    );

    await userEvent.click(screen.getByRole('button', { name: 'Nova pastoral' }));
    const dialogo = await screen.findByRole('dialog', { name: 'Nova pastoral' });
    await userEvent.type(within(dialogo).getByLabelText('Nome'), 'ECC');
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Criar pastoral' }));

    expect(await screen.findByText('Pastoral criada')).toBeInTheDocument();
    await waitFor(() => {
      expect(corpo).toMatchObject({ nome: 'ECC' });
    });
  });
});

describe('Financeiro consolidado (área do padre)', () => {
  it('mostra os totais e o detalhamento por pastoral', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(padre)),
      http.get('/api/financeiro/resumo', () =>
        HttpResponse.json({
          totalEntradas: 500,
          totalSaidas: 200,
          saldo: 300,
          porPastoral: [
            {
              pastoralId: 2,
              pastoralNome: 'Pascom',
              totalEntradas: 500,
              totalSaidas: 200,
              saldo: 300,
            },
          ],
        }),
      ),
    );
    renderizarRotas('/financeiro');

    expect(await screen.findAllByText('R$ 300,00')).toHaveLength(2);
    expect(screen.getAllByText('Pascom').length).toBeGreaterThan(0);
  });
});
