import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { servidor } from '../test/servidor';
import { renderizarRotas } from '../test/utilidades';
import { ExigePerfil } from './rotas';
import { comProvedores } from '../test/utilidades';
import { cleanup, render } from '@testing-library/react';

const membro = {
  id: 10,
  nome: 'Maria',
  email: 'maria@exemplo.com',
  perfil: 'SERVIDOR',
  paroquiaId: 1,
  pastorais: [{ id: 1, nome: 'Pascom', papel: 'MEMBRO' }],
};

describe('guardas de rota', () => {
  it('sem sessão, vai ao login e lembra de onde veio', async () => {
    servidor.use(http.get('/api/me', () => HttpResponse.json({ detail: 'x' }, { status: 401 })));
    const { roteador } = renderizarRotas('/conta/senha');
    await waitFor(() => {
      expect(roteador.state.location.pathname).toBe('/entrar');
    });
    expect(roteador.state.location.state).toEqual({ de: '/conta/senha' });
  });

  it('perfil sem permissão vê a tela de acesso, não uma falha', async () => {
    servidor.use(http.get('/api/me', () => HttpResponse.json(membro)));
    renderizarRotas('/conta/senha', [
      {
        element: <ExigePerfil perfis={['PADRE', 'ADMIN']} />,
        children: [{ path: '/conta/senha', element: <p>área do padre</p> }],
      },
    ]);
    expect(
      await screen.findByText('Esta área não está disponível para o seu acesso'),
    ).toBeInTheDocument();
    expect(screen.queryByText('área do padre')).not.toBeInTheDocument();
  });
});

describe('pastoral ativa', () => {
  it('trocar a pastoral grava a escolha e troca a cor do tema', async () => {
    servidor.use(
      http.get('/api/me', () =>
        HttpResponse.json({
          ...membro,
          pastorais: [
            { id: 1, nome: 'Pascom', papel: 'MEMBRO' },
            { id: 2, nome: 'ECC', papel: 'MEMBRO' },
          ],
        }),
      ),
    );
    function Sonda() {
      const sessao = useSessao();
      return (
        <>
          <p>ativa: {sessao.pastoralAtiva?.nome ?? '-'}</p>
          <button
            onClick={() => {
              sessao.trocarPastoral(2);
            }}
          >
            trocar para ECC
          </button>
        </>
      );
    }
    render(comProvedores(<Sonda />));
    expect(await screen.findByText('ativa: Pascom')).toBeInTheDocument();
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#1d4ed8');

    await userEvent.click(screen.getByRole('button', { name: 'trocar para ECC' }));
    expect(await screen.findByText('ativa: ECC')).toBeInTheDocument();
    expect(window.localStorage.getItem('servio.pastoralAtiva')).toBe('2');
    expect(document.documentElement.style.getPropertyValue('--accent')).toBe('#b91c1c');
    cleanup();
  });
});

import { useSessao } from './sessaoContexto';
