import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { servidor } from '../../test/servidor';
import { Convite } from './Convite';

const detalhes = {
  primeiroNome: 'Maria',
  celebracaoTitulo: 'Missa dominical',
  data: '2031-03-02',
  hora: '19:00:00',
  horarioChegada: '18:30:00',
  funcao: 'Comunicação',
  observacao: 'Levar o microfone',
  prazo: '2031-03-01T18:00:00',
  status: 'PENDENTE',
};

function abrirComToken(token: string | null) {
  window.history.replaceState(null, '', token ? `/convite#${token}` : '/convite');
  return render(<Convite />);
}

describe('Página do convite', () => {
  it('tira o token do fragmento e mostra saudação, horário de chegada e função', async () => {
    servidor.use(http.post('/api/confirmacoes/detalhes', () => HttpResponse.json(detalhes)));
    abrirComToken('token-secreto');

    expect(await screen.findByText('Olá, Maria!')).toBeInTheDocument();
    expect(screen.getByText('18:30')).toBeInTheDocument();
    expect(screen.getByText('Comunicação')).toBeInTheDocument();
    expect(screen.getByText('domingo, 2 de março de 2031')).toBeInTheDocument();
    expect(window.location.hash).toBe('');
    expect(window.location.href).not.toContain('token-secreto');
  });

  it('confirmar presença envia aceitar=true e mostra a confirmação', async () => {
    let corpo: unknown = null;
    servidor.use(
      http.post('/api/confirmacoes/detalhes', () => HttpResponse.json(detalhes)),
      http.post('/api/confirmacoes/responder', async ({ request }) => {
        corpo = await request.json();
        return HttpResponse.json({ status: 'ACEITA' });
      }),
    );
    abrirComToken('tok');
    await userEvent.click(await screen.findByRole('button', { name: 'Confirmo minha presença' }));

    expect(await screen.findByText('Presença confirmada. Obrigado!')).toBeInTheDocument();
    expect(corpo).toMatchObject({ token: 'tok', aceitar: true });
  });

  it('recusar pede o motivo e envia a justificativa', async () => {
    let corpo: unknown = null;
    servidor.use(
      http.post('/api/confirmacoes/detalhes', () => HttpResponse.json(detalhes)),
      http.post('/api/confirmacoes/responder', async ({ request }) => {
        corpo = await request.json();
        return HttpResponse.json({ status: 'RECUSADA' });
      }),
    );
    abrirComToken('tok');
    await userEvent.click(await screen.findByRole('button', { name: 'Não poderei ir' }));
    await userEvent.type(screen.getByRole('textbox'), 'Vou viajar');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar recusa' }));

    expect(await screen.findByText('Recusa registrada')).toBeInTheDocument();
    expect(corpo).toMatchObject({ token: 'tok', aceitar: false, justificativa: 'Vou viajar' });
  });

  it('404 diz que o convite não está mais disponível, sem dizer qual caso é', async () => {
    servidor.use(
      http.post('/api/confirmacoes/detalhes', () =>
        HttpResponse.json({ detail: 'x' }, { status: 404 }),
      ),
    );
    abrirComToken('tok');
    expect(await screen.findByText(/Este convite não está mais disponível/)).toBeInTheDocument();
  });

  it('410 mostra que o prazo terminou', async () => {
    servidor.use(
      http.post('/api/confirmacoes/detalhes', () =>
        HttpResponse.json({ detail: 'x' }, { status: 410 }),
      ),
    );
    abrirComToken('tok');
    expect(await screen.findByText('O prazo para responder terminou')).toBeInTheDocument();
  });

  it('429 pede para aguardar', async () => {
    servidor.use(
      http.post('/api/confirmacoes/detalhes', () =>
        HttpResponse.json({ detail: 'x' }, { status: 429 }),
      ),
    );
    abrirComToken('tok');
    expect(await screen.findByText('Muitas tentativas')).toBeInTheDocument();
  });

  it('erro de conexão oferece tentar de novo, e a nova tentativa funciona', async () => {
    let tentativa = 0;
    servidor.use(
      http.post('/api/confirmacoes/detalhes', () => {
        tentativa += 1;
        if (tentativa === 1) return HttpResponse.error();
        return HttpResponse.json(detalhes);
      }),
    );
    abrirComToken('tok');
    await userEvent.click(await screen.findByRole('button', { name: 'Tentar de novo' }));
    expect(await screen.findByText('Olá, Maria!')).toBeInTheDocument();
  });

  it('sem token, não há o que mostrar', async () => {
    abrirComToken(null);
    expect(await screen.findByText('Convite não encontrado')).toBeInTheDocument();
  });
});
