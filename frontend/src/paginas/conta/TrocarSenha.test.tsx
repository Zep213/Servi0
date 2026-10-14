import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { servidor } from '../../test/servidor';
import { renderizarRotas } from '../../test/utilidades';

const usuario = {
  id: 10,
  nome: 'Maria',
  email: 'maria@exemplo.com',
  perfil: 'SERVIDOR',
  paroquiaId: 1,
  pastorais: [{ id: 1, nome: 'Pascom', papel: 'MEMBRO' }],
};

async function preencher(senhaAtual: string, senhaNova: string, confirmacao: string) {
  await userEvent.type(await screen.findByLabelText('Senha atual'), senhaAtual);
  await userEvent.type(screen.getByLabelText('Senha nova'), senhaNova);
  await userEvent.type(screen.getByLabelText('Repita a senha nova'), confirmacao);
  await userEvent.click(screen.getByRole('button', { name: 'Trocar senha' }));
}

describe('Trocar senha', () => {
  it('senhas diferentes não chegam ao servidor', async () => {
    servidor.use(http.get('/api/me', () => HttpResponse.json(usuario)));
    renderizarRotas('/conta/senha');
    await preencher('atual-123', 'nova-12345', 'outra-12345');
    expect(await screen.findByText('As senhas não são iguais')).toBeInTheDocument();
  });

  it('senha atual errada (422) aparece no campo da senha atual', async () => {
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(usuario)),
      http.post('/api/me/senha', () =>
        HttpResponse.json({ detail: 'Senha atual incorreta' }, { status: 422 }),
      ),
    );
    renderizarRotas('/conta/senha');
    await preencher('errada', 'nova-12345', 'nova-12345');
    await waitFor(() => {
      expect(screen.getByLabelText('Senha atual')).toHaveAttribute('aria-invalid', 'true');
    });
    expect(screen.getByText('Senha atual incorreta')).toBeInTheDocument();
  });

  it('troca bem-sucedida leva ao login com aviso (a sessão caiu)', async () => {
    let logado = true;
    servidor.use(
      http.get('/api/me', () =>
        logado ? HttpResponse.json(usuario) : HttpResponse.json({ detail: 'x' }, { status: 401 }),
      ),
      http.post('/api/me/senha', () => {
        logado = false;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const { roteador } = renderizarRotas('/conta/senha');
    await preencher('atual-123', 'nova-12345', 'nova-12345');
    await waitFor(() => {
      expect(roteador.state.location.pathname).toBe('/entrar');
    });
    await waitFor(() => {
      expect(roteador.state.location.state).toMatchObject({
        aviso: 'Senha trocada. Entre de novo com a senha nova.',
      });
    });
  });
});
