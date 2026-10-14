import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { servidor } from '../test/servidor';
import { comProvedores } from '../test/utilidades';
import { AvisoAtualizacao } from './AvisoAtualizacao';
import { ConviteInstalar } from './ConviteInstalar';

const definirPrecisaAtualizar = vi.fn();
const atualizarServiceWorker = vi.fn();
let precisaAtualizar = false;

vi.mock('virtual:pwa-register/react', () => ({
  useRegisterSW: () => ({
    needRefresh: [precisaAtualizar, definirPrecisaAtualizar],
    offlineReady: [false, vi.fn()],
    updateServiceWorker: atualizarServiceWorker,
  }),
}));

describe('AvisoAtualizacao', () => {
  it('sem versão nova, não mostra nada', () => {
    precisaAtualizar = false;
    render(<AvisoAtualizacao />);
    expect(screen.queryByText('Nova versão disponível.')).not.toBeInTheDocument();
  });

  it('com versão nova, oferece atualizar', async () => {
    precisaAtualizar = true;
    render(<AvisoAtualizacao />);
    expect(screen.getByText('Nova versão disponível.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Atualizar' }));
    expect(atualizarServiceWorker).toHaveBeenCalledWith(true);
  });
});

describe('ConviteInstalar', () => {
  const eu = {
    id: 1,
    nome: 'Maria',
    email: 'maria@exemplo.com',
    perfil: 'SERVIDOR',
    paroquiaId: 1,
    pastorais: [],
  };

  it('sem o navegador oferecer, não mostra nada', () => {
    servidor.use(http.get('/api/me', () => HttpResponse.json(eu)));
    render(comProvedores(<ConviteInstalar />));
    expect(screen.queryByText('Instalar o Servio neste aparelho?')).not.toBeInTheDocument();
  });

  it('quando o navegador oferece instalar, mostra o convite e aciona o prompt', async () => {
    servidor.use(http.get('/api/me', () => HttpResponse.json(eu)));
    render(comProvedores(<ConviteInstalar />));

    const prompt = vi.fn().mockResolvedValue(undefined);
    const evento = new Event('beforeinstallprompt', { cancelable: true });
    Object.assign(evento, { prompt });
    window.dispatchEvent(evento);

    expect(await screen.findByText('Instalar o Servio neste aparelho?')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Instalar' }));
    expect(prompt).toHaveBeenCalled();
  });
});
