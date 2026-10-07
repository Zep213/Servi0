import { createBrowserRouter } from 'react-router';
import { Casca } from './paginas/Casca';
import { ErroGeral } from './paginas/ErroGeral';
import { Entrar } from './paginas/publico/Entrar';
import { Convite } from './paginas/publico/Convite';
import { NaoEncontrada } from './paginas/publico/NaoEncontrada';
import { TrocarSenha } from './paginas/conta/TrocarSenha';
import { ExigeParticipa, ExigeSessao, ExigeVeGestao, RedirecionaInicio } from './auth/rotas';
import { Painel } from './paginas/pastoral/Painel';
import { EscalaCelebracao } from './paginas/pastoral/EscalaCelebracao';
import { Membros } from './paginas/pastoral/Membros';
import { Reunioes } from './paginas/pastoral/Reunioes';
import { Financeiro } from './paginas/pastoral/Financeiro';
import { Configuracoes } from './paginas/pastoral/Configuracoes';
import { MinhasEscalas } from './paginas/servidor/MinhasEscalas';
import { Indisponibilidades } from './paginas/servidor/Indisponibilidades';

/** Mapa de rotas. Cada área entra aqui quando a sua parte é feita. */
export const rotas = [
  { path: '/entrar', element: <Entrar />, errorElement: <ErroGeral /> },
  // público: quem recebe o convite não tem conta no app
  { path: '/convite', element: <Convite />, errorElement: <ErroGeral /> },
  {
    element: <ExigeSessao />,
    errorElement: <ErroGeral />,
    children: [
      {
        element: <Casca />,
        children: [
          { index: true, element: <RedirecionaInicio /> },
          { path: '/conta/senha', element: <TrocarSenha /> },
          { path: '/minhas-escalas', element: <MinhasEscalas /> },
          { path: '/indisponibilidades', element: <Indisponibilidades /> },
          {
            path: '/pastoral/:pastoralId',
            children: [
              {
                element: <ExigeVeGestao />,
                children: [
                  { path: 'painel', element: <Painel /> },
                  { path: 'celebracoes/:celebracaoId', element: <EscalaCelebracao /> },
                  { path: 'membros', element: <Membros /> },
                  { path: 'financeiro', element: <Financeiro /> },
                  { path: 'configuracoes', element: <Configuracoes /> },
                ],
              },
              {
                element: <ExigeParticipa />,
                children: [{ path: 'reunioes', element: <Reunioes /> }],
              },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NaoEncontrada /> },
];

export const roteador = createBrowserRouter(rotas);
