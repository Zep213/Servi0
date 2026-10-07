import { Link, NavLink, Outlet } from 'react-router';
import { Botao } from '../componentes/Botao';
import { Icone } from '../componentes/Icone';
import { useSessao } from '../auth/sessaoContexto';
import { acessoNaPastoral } from '../auth/acesso';

/**
 * Moldura do app: barra lateral no computador, navegação inferior no celular. Os itens mudam
 * com o papel na pastoral ativa; a tela também confere (o front só esconde).
 */
export function Casca() {
  const { usuario, pastorais, pastoralAtiva, trocarPastoral, encerrar } = useSessao();

  const pid = pastoralAtiva ? String(pastoralAtiva.id) : null;
  const acesso = pastoralAtiva
    ? acessoNaPastoral(usuario?.perfil ?? undefined, pastorais, pastoralAtiva.id)
    : null;
  const ehPadreOuAdmin = usuario?.perfil === 'PADRE' || usuario?.perfil === 'ADMIN';
  const itens = [
    { para: '/minhas-escalas', rotulo: 'Minhas escalas', icone: 'calendario' as const },
    { para: '/indisponibilidades', rotulo: 'Indisponibilidades', icone: 'relogio' as const },
    ...(ehPadreOuAdmin
      ? [
          { para: '/celebracoes', rotulo: 'Celebrações', icone: 'calendario' as const },
          { para: '/pastorais', rotulo: 'Pastorais', icone: 'pessoas' as const },
          { para: '/financeiro', rotulo: 'Financeiro', icone: 'financeiro' as const },
        ]
      : []),
    ...(pid && acesso?.veGestao
      ? [
          { para: `/pastoral/${pid}/painel`, rotulo: 'Painel', icone: 'painel' as const },
          { para: `/pastoral/${pid}/membros`, rotulo: 'Membros', icone: 'pessoas' as const },
          {
            para: `/pastoral/${pid}/financeiro`,
            rotulo: 'Financeiro',
            icone: 'financeiro' as const,
          },
          ...(acesso.coordena
            ? [
                {
                  para: `/pastoral/${pid}/configuracoes`,
                  rotulo: 'Configurações',
                  icone: 'configuracoes' as const,
                },
              ]
            : []),
        ]
      : []),
    ...(pid &&
    (acesso?.papel !== null || usuario?.perfil === 'PADRE' || usuario?.perfil === 'ADMIN')
      ? [{ para: `/pastoral/${pid}/reunioes`, rotulo: 'Reuniões', icone: 'reuniao' as const }]
      : []),
  ];

  return (
    <div className="casca">
      <aside className="casca__lateral" aria-label="Menu">
        <Link to="/" className="casca__marca">
          Servio
        </Link>
        <nav>
          <ul className="casca__lista">
            {itens.map((i) => (
              <li key={i.para}>
                <NavLink to={i.para} className="casca__link">
                  <Icone nome={i.icone} />
                  <span>{i.rotulo}</span>
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </aside>
      <div className="casca__conteudo">
        <header className="casca__topo">
          {pastorais.length > 1 ? (
            <label className="casca__troca">
              <span className="sr-only">Pastoral</span>
              <select
                value={pastoralAtiva?.id ?? ''}
                onChange={(e) => {
                  trocarPastoral(Number(e.target.value));
                }}
              >
                {pastorais.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.nome}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <span className="casca__pastoral">{pastoralAtiva?.nome ?? ''}</span>
          )}
          <span className="casca__usuario">{usuario?.nome}</span>
          <Link to="/conta/senha" className="casca__link-conta">
            Trocar senha
          </Link>
          <Botao variante="secundario" onClick={encerrar}>
            <Icone nome="sair" />
            <span>Sair</span>
          </Botao>
        </header>
        <main className="casca__pagina">
          <Outlet />
        </main>
      </div>
      <nav className="casca__inferior" aria-label="Navegação">
        {itens.map((i) => (
          <NavLink key={i.para} to={i.para} className="casca__inferior-link">
            <Icone nome={i.icone} />
            <span>{i.rotulo}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
