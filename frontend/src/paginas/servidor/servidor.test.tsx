import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { servidor } from '../../test/servidor';
import { renderizarRotas } from '../../test/utilidades';

const eu = {
  id: 10,
  nome: 'Maria Souza',
  email: 'maria@exemplo.com',
  perfil: 'SERVIDOR',
  paroquiaId: 1,
  pastorais: [{ id: 1, nome: 'Pascom', papel: 'MEMBRO' }],
};

const escalaPendente = {
  alocacaoId: 77,
  status: 'PENDENTE',
  dataLimiteResposta: '2031-03-01T18:00:00',
  origem: 'SORTEIO',
  horarioChegada: '18:30:00',
  observacao: 'Levar o microfone',
  celebracao: {
    id: 1,
    titulo: 'Missa dominical',
    tipo: 'MISSA_DOMINICAL',
    data: '2031-03-02',
    hora: '19:00:00',
  },
  funcao: { id: 3, nome: 'Comunicação' },
  pastoral: { id: 1, nome: 'Pascom' },
};

describe('Minhas escalas', () => {
  it('mostra a escala com status, horário de chegada e os dois botões de resposta', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/me/escalas', () => HttpResponse.json([escalaPendente])),
    );
    renderizarRotas('/minhas-escalas');

    expect(await screen.findByText('Missa dominical')).toBeInTheDocument();
    expect(screen.getByText('Aguardando resposta')).toBeInTheDocument();
    expect(screen.getByText('18:30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmo minha presença' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Não poderei ir' })).toBeInTheDocument();
  });

  it('confirmar envia aceitar=true para a alocação certa e avisa', async () => {
    let consulta: URL | null = null;
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/me/escalas', () => HttpResponse.json([escalaPendente])),
      http.post('/api/alocacoes/:id/responder', ({ request, params }) => {
        consulta = new URL(request.url);
        expect(params.id).toBe('77');
        return HttpResponse.json({ id: 77, status: 'ACEITA' });
      }),
    );
    renderizarRotas('/minhas-escalas');
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmo minha presença' }));

    expect(await screen.findByText('Presença confirmada. Obrigado!')).toBeInTheDocument();
    await waitFor(() => {
      expect(consulta?.searchParams.get('aceitar')).toBe('true');
    });
  });

  it('recusar pede o motivo e manda a justificativa', async () => {
    let consulta: URL | null = null;
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/me/escalas', () => HttpResponse.json([escalaPendente])),
      http.post('/api/alocacoes/:id/responder', ({ request }) => {
        consulta = new URL(request.url);
        return HttpResponse.json({ id: 77, status: 'RECUSADA' });
      }),
    );
    renderizarRotas('/minhas-escalas');
    await userEvent.click(await screen.findByRole('button', { name: 'Não poderei ir' }));
    await userEvent.type(screen.getByRole('textbox'), 'Vou viajar');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar recusa' }));

    expect(await screen.findByText('Recusa registrada')).toBeInTheDocument();
    await waitFor(() => {
      expect(consulta?.searchParams.get('aceitar')).toBe('false');
      expect(consulta?.searchParams.get('justificativa')).toBe('Vou viajar');
    });
  });

  it('a aba Passadas pede um período que termina antes de hoje e não tem botões', async () => {
    const periodos: { de: string | null; ate: string | null }[] = [];
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/me/escalas', ({ request }) => {
        const url = new URL(request.url);
        periodos.push({ de: url.searchParams.get('de'), ate: url.searchParams.get('ate') });
        return HttpResponse.json([{ ...escalaPendente, status: 'ACEITA' }]);
      }),
    );
    renderizarRotas('/minhas-escalas');
    await screen.findByText('Missa dominical');
    await userEvent.click(screen.getByRole('button', { name: 'Passadas' }));

    await waitFor(() => {
      const ultimo = periodos[periodos.length - 1];
      expect(ultimo?.ate && ultimo.ate < new Date().toISOString().slice(0, 10)).toBe(true);
    });
    expect(
      screen.queryByRole('button', { name: 'Confirmo minha presença' }),
    ).not.toBeInTheDocument();
  });

  it('sem escalas, diz isso com texto simples', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/me/escalas', () => HttpResponse.json([])),
    );
    renderizarRotas('/minhas-escalas');
    expect(await screen.findByText('Você não tem escalas marcadas')).toBeInTheDocument();
  });
});

describe('Minhas indisponibilidades', () => {
  const minha = {
    id: 1,
    usuarioId: 10,
    dataInicio: '2031-03-10',
    dataFim: '2031-03-12',
    motivo: 'Viagem',
  };
  const deOutra = {
    id: 2,
    usuarioId: 99,
    dataInicio: '2031-04-01',
    dataFim: '2031-04-02',
    motivo: 'Outra pessoa',
  };

  it('lista só as minhas, mesmo que a API devolva de outras pessoas', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/indisponibilidades', () => HttpResponse.json({ content: [minha, deOutra] })),
    );
    renderizarRotas('/indisponibilidades');
    expect(await screen.findByText('Viagem')).toBeInTheDocument();
    expect(screen.queryByText('Outra pessoa')).not.toBeInTheDocument();
  });

  it('data final antes da inicial não chega ao servidor', async () => {
    let chamou = false;
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/indisponibilidades', () => HttpResponse.json({ content: [] })),
      http.post('/api/indisponibilidades', () => {
        chamou = true;
        return HttpResponse.json(minha, { status: 201 });
      }),
    );
    renderizarRotas('/indisponibilidades');
    await screen.findByText('Nenhuma indisponibilidade marcada');
    await userEvent.type(screen.getByLabelText('Data inicial'), '2031-03-10');
    await userEvent.type(screen.getByLabelText('Data final'), '2031-03-05');
    await userEvent.click(screen.getByRole('button', { name: 'Marcar período' }));

    expect(
      await screen.findByText('A data final não pode ser antes da inicial'),
    ).toBeInTheDocument();
    expect(chamou).toBe(false);
  });

  it('marcar envia o período com o meu id', async () => {
    let corpo: unknown = null;
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/indisponibilidades', () => HttpResponse.json({ content: [] })),
      http.post('/api/indisponibilidades', async ({ request }) => {
        corpo = await request.json();
        return HttpResponse.json(minha, { status: 201 });
      }),
    );
    renderizarRotas('/indisponibilidades');
    await screen.findByText('Nenhuma indisponibilidade marcada');
    await userEvent.type(screen.getByLabelText('Data inicial'), '2031-03-10');
    await userEvent.type(screen.getByLabelText('Data final'), '2031-03-12');
    await userEvent.type(screen.getByLabelText('Motivo (opcional)'), 'Viagem');
    await userEvent.click(screen.getByRole('button', { name: 'Marcar período' }));

    await waitFor(() => {
      expect(corpo).toMatchObject({
        usuarioId: 10,
        dataInicio: '2031-03-10',
        dataFim: '2031-03-12',
        motivo: 'Viagem',
      });
    });
  });

  it('apagar pede confirmação e só então remove', async () => {
    let apagado: string | null = null;
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/indisponibilidades', () => HttpResponse.json({ content: [minha] })),
      http.delete('/api/indisponibilidades/:id', ({ params }) => {
        apagado = String(params.id);
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderizarRotas('/indisponibilidades');
    const linha = (await screen.findByText('Viagem')).closest('tr') as HTMLElement;
    await userEvent.click(within(linha).getByRole('button', { name: 'Apagar' }));

    const dialogo = await screen.findByRole('dialog', { name: 'Apagar esta indisponibilidade?' });
    expect(apagado).toBeNull();
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Apagar' }));
    await waitFor(() => {
      expect(apagado).toBe('1');
    });
  });
});
