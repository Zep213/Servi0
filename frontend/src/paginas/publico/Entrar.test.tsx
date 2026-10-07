import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { servidor } from '../../test/servidor';
import { renderizarRotas } from '../../test/utilidades';

const membro = {
  id: 10,
  nome: 'Maria Souza',
  email: 'maria@exemplo.com',
  perfil: 'SERVIDOR',
  paroquiaId: 1,
  pastorais: [{ id: 1, nome: 'Pascom', papel: 'MEMBRO' }],
};

describe('Entrar', () => {
  it('entra, busca quem é e segue para o destino do papel', async () => {
    let logado = false;
    servidor.use(
      http.post('/api/auth/login', async ({ request }) => {
        const corpo = new URLSearchParams(await request.text());
        expect(corpo.get('email')).toBe('maria@exemplo.com');
        logado = true;
        return new HttpResponse(null, { status: 204 });
      }),
      http.get('/api/me', () =>
        logado ? HttpResponse.json(membro) : HttpResponse.json({ detail: 'x' }, { status: 401 }),
      ),
    );

    const { roteador } = renderizarRotas('/entrar');
    await userEvent.type(await screen.findByLabelText('E-mail'), 'maria@exemplo.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-123');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => {
      expect(roteador.state.location.pathname).toBe('/minhas-escalas');
    });
  });

  it('mostra "E-mail ou senha inválidos" para 401', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json({ detail: 'x' }, { status: 401 })),
      http.post('/api/auth/login', () => HttpResponse.json({ detail: 'x' }, { status: 401 })),
    );
    renderizarRotas('/entrar');
    await userEvent.type(await screen.findByLabelText('E-mail'), 'maria@exemplo.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'errada');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('E-mail ou senha inválidos');
  });

  it('mostra a mensagem de muitas tentativas para 429', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json({ detail: 'x' }, { status: 401 })),
      http.post('/api/auth/login', () => HttpResponse.json({ detail: 'x' }, { status: 429 })),
    );
    renderizarRotas('/entrar');
    await userEvent.type(await screen.findByLabelText('E-mail'), 'maria@exemplo.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'qualquer');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Muitas tentativas, aguarde alguns minutos',
    );
  });

  it('valida o e-mail antes de enviar', async () => {
    servidor.use(http.get('/api/me', () => HttpResponse.json({ detail: 'x' }, { status: 401 })));
    renderizarRotas('/entrar');
    await userEvent.type(await screen.findByLabelText('E-mail'), 'nao-e-email');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));
    expect(await screen.findByText('Informe um e-mail válido')).toBeInTheDocument();
  });
});
