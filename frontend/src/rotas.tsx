import { createBrowserRouter } from 'react-router';
import { Casca } from './paginas/Casca';
import { ErroGeral } from './paginas/ErroGeral';
import { Entrar } from './paginas/publico/Entrar';
import { NaoEncontrada } from './paginas/publico/NaoEncontrada';
import { TrocarSenha } from './paginas/conta/TrocarSenha';
import { ExigeSessao, RedirecionaInicio } from './auth/rotas';

/** Mapa de rotas. Cada área entra aqui quando a sua parte é feita. */
export const rotas = [
  { path: '/entrar', element: <Entrar />, errorElement: <ErroGeral /> },
  {
    element: <ExigeSessao />,
    errorElement: <ErroGeral />,
    children: [
      {
        element: <Casca />,
        children: [
          { index: true, element: <RedirecionaInicio /> },
          { path: '/conta/senha', element: <TrocarSenha /> },
        ],
      },
    ],
  },
  { path: '*', element: <NaoEncontrada /> },
];

export const roteador = createBrowserRouter(rotas);
