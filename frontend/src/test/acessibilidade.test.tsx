import { render, screen } from '@testing-library/react';
import { axe } from 'jest-axe';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { Convite } from '../paginas/publico/Convite';
import { servidor } from './servidor';
import { renderizarRotas } from './utilidades';

/**
 * jsdom não renderiza de verdade, então contraste de cor não dá para checar aqui (já é
 * conferido visualmente pelos tokens do tema). O resto das regras do axe vale.
 */
const OPCOES = { rules: { 'color-contrast': { enabled: false } } };

async function semViolacoesSerias(container: Element) {
  const resultado = await axe(container, OPCOES);
  expect(resultado).toHaveNoViolations();
}

const naoLogado = () =>
  http.get('/api/me', () => HttpResponse.json({ detail: 'não autenticado' }, { status: 401 }));

describe('Acessibilidade (axe) das páginas principais', () => {
  it('Entrar', async () => {
    servidor.use(naoLogado());
    renderizarRotas('/entrar');
    await screen.findByRole('button', { name: 'Entrar' });
    await semViolacoesSerias(document.body);
  });

  it('Convite', async () => {
    servidor.use(
      http.post('/api/confirmacoes/detalhes', () =>
        HttpResponse.json({
          primeiroNome: 'Maria',
          celebracaoTitulo: 'Missa dominical',
          data: '2031-03-02',
          hora: '19:00:00',
          funcao: 'Comunicação',
          prazo: '2031-03-01T18:00:00',
          status: 'PENDENTE',
        }),
      ),
    );
    window.history.replaceState(null, '', '/convite#token-secreto');
    const { container } = render(<Convite />);
    await screen.findByText('Olá, Maria!');
    await semViolacoesSerias(container);
  });

  it('Minhas escalas', async () => {
    const eu = {
      id: 10,
      nome: 'Maria Souza',
      email: 'maria@exemplo.com',
      perfil: 'SERVIDOR',
      paroquiaId: 1,
      pastorais: [{ id: 1, nome: 'Pascom', papel: 'MEMBRO' }],
    };
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/me/escalas', () => HttpResponse.json([])),
    );
    renderizarRotas('/minhas-escalas');
    await screen.findByText('Você não tem escalas marcadas');
    await semViolacoesSerias(document.body);
  });

  it('Painel da pastoral', async () => {
    const eu = {
      id: 10,
      nome: 'Maria Souza',
      email: 'maria@exemplo.com',
      perfil: 'SERVIDOR',
      paroquiaId: 1,
      pastorais: [{ id: 1, nome: 'Pascom', papel: 'COORDENADOR' }],
    };
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(eu)),
      http.get('/api/pastorais/:id/painel', () =>
        HttpResponse.json({
          cards: { vagasTotais: 2, vagasOcupadas: 1, convitesPendentes: 0, alteracoesPendentes: 0 },
          celebracoes: [
            {
              id: 1,
              data: '2031-03-02',
              hora: '19:00:00',
              titulo: 'Missa dominical',
              tipo: 'MISSA_DOMINICAL',
              vagasTotais: 2,
              ocupadas: 1,
              status: 'PENDENTE',
              aguardandoQuantidade: false,
            },
          ],
          pendencias: { convitesVencendo: [], recusasSemSubstituto: [], alteracoesAguardando: [] },
        }),
      ),
    );
    renderizarRotas('/pastoral/1/painel');
    await screen.findByText('Vagas no mês');
    await semViolacoesSerias(document.body);
  });

  it('Celebrações (área do padre)', async () => {
    const padre = {
      id: 1,
      nome: 'Padre João',
      email: 'padre@exemplo.com',
      perfil: 'PADRE',
      paroquiaId: 1,
      pastorais: [],
    };
    servidor.use(
      http.get('/api/me', () => HttpResponse.json(padre)),
      http.get('/api/celebracoes', () =>
        HttpResponse.json({
          content: [
            {
              id: 1,
              comunidadeId: 5,
              data: '2031-03-02',
              hora: '19:00:00',
              tipoData: 'NORMAL',
              tipo: 'MISSA_DOMINICAL',
              titulo: 'Missa dominical',
            },
          ],
        }),
      ),
      http.get('/api/comunidades', () =>
        HttpResponse.json({ content: [{ id: 5, nome: 'Matriz' }] }),
      ),
    );
    renderizarRotas('/celebracoes');
    await screen.findByText('Matriz');
    await semViolacoesSerias(document.body);
  });
});
