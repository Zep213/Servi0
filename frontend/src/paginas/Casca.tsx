import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useMatch, useNavigate } from 'react-router';
import { Botao } from '../componentes/Botao';
import { Icone, type NomeIcone } from '../componentes/Icone';
import { Modal } from '../componentes/Modal';
import { useSessao } from '../auth/sessaoContexto';
import { acessoNaPastoral } from '../auth/acesso';
import { destinoInicial } from '../auth/destino';

/**
 * Moldura do app: barra lateral no computador, navegação inferior no celular. Os itens mudam
 * com o papel na pastoral ativa; a tela também confere (o front só esconde).
 */
export function Casca() {
  const { usuario, pastorais, pastoralAtiva, trocarPastoral, encerrar } = useSessao();
  const navegar = useNavigate();
  const local = useLocation();
  const barraInferior = useRef<HTMLElement>(null);

  // Quem abre /pastoral/2/... (link, favorito, voltar do navegador) passa a trabalhar na 2. Só
  // reage quando a URL muda: trocar no seletor já navega para a nova pastoral.
  const idNaUrl = Number(useMatch('/pastoral/:pastoralId/*')?.params.pastoralId ?? NaN);
  const sincronizado = useRef<number | null>(null);
  useEffect(() => {
    if (!Number.isInteger(idNaUrl) || idNaUrl === sincronizado.current) return;
    if (!pastorais.some((p) => p.id === idNaUrl)) return;
    sincronizado.current = idNaUrl;
    trocarPastoral(idNaUrl);
  }, [idNaUrl, pastorais, trocarPastoral]);

  useEffect(() => {
    barraInferior.current
      ?.querySelector('[aria-current="page"]')
      ?.scrollIntoView({ block: 'nearest', inline: 'center' });
  }, [local.pathname]);

  const escolherPastoral = (id: number) => {
    trocarPastoral(id);
    if (Number.isInteger(idNaUrl) && usuario) {
      sincronizado.current = id;
      void navegar(destinoInicial(usuario, pastorais, id));
    }
  };

  const pid = pastoralAtiva ? String(pastoralAtiva.id) : null;
  const acesso = pastoralAtiva
    ? acessoNaPastoral(usuario?.perfil ?? undefined, pastorais, pastoralAtiva.id)
    : null;
  const ehPadreOuAdmin = usuario?.perfil === 'PADRE' || usuario?.perfil === 'ADMIN';
  const itens = [
    {
      para: '/minhas-escalas',
      rotulo: 'Minhas escalas',
      icone: 'calendario' as const,
      prioridade: 2,
    },
    {
      para: '/indisponibilidades',
      rotulo: 'Indisponibilidades',
      icone: 'relogio' as const,
      prioridade: 5,
    },
    ...(ehPadreOuAdmin
      ? [
          {
            para: '/celebracoes',
            rotulo: 'Celebrações',
            icone: 'calendario' as const,
            prioridade: 1,
          },
          { para: '/pastorais', rotulo: 'Pastorais', icone: 'pessoas' as const, prioridade: 3 },
          {
            para: '/financeiro',
            rotulo: 'Financeiro',
            icone: 'financeiro' as const,
            prioridade: 6,
          },
        ]
      : []),
    ...(pid && acesso?.veGestao
      ? [
          {
            para: `/pastoral/${pid}/painel`,
            rotulo: 'Painel',
            icone: 'painel' as const,
            prioridade: 1,
          },
          {
            para: `/pastoral/${pid}/membros`,
            rotulo: 'Membros',
            icone: 'pessoas' as const,
            prioridade: 3,
          },
          {
            para: `/pastoral/${pid}/financeiro`,
            rotulo: 'Financeiro',
            icone: 'financeiro' as const,
            prioridade: 6,
          },
          ...(acesso.coordena
            ? [
                {
                  para: `/pastoral/${pid}/configuracoes`,
                  rotulo: 'Configurações',
                  icone: 'configuracoes' as const,
                  prioridade: 7,
                },
              ]
            : []),
        ]
      : []),
    ...(pid &&
    (acesso?.papel !== null || usuario?.perfil === 'PADRE' || usuario?.perfil === 'ADMIN')
      ? [
          {
            para: `/pastoral/${pid}/reunioes`,
            rotulo: 'Reuniões',
            icone: 'reuniao' as const,
            prioridade: 4,
          },
        ]
      : []),
  ];

  // Celular: até 5 itens cabem; acima disso ficam os 4 mais usados e o resto vai para "Mais".
  const [maisAberto, definirMaisAberto] = useState(false);
  const porPrioridade = [...itens].sort((a, b) => a.prioridade - b.prioridade);
  const naBarra = itens.length > 5 ? porPrioridade.slice(0, 4) : itens;
  const noMais = itens.length > 5 ? porPrioridade.slice(4) : [];
  const paginaNoMais = noMais.some((i) => local.pathname.startsWith(i.para));

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
                  escolherPastoral(Number(e.target.value));
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
      <nav className="casca__inferior" aria-label="Navegação" ref={barraInferior}>
        {naBarra.map((i) => (
          <NavLink key={i.para} to={i.para} className="casca__inferior-link">
            <Icone nome={i.icone} />
            <span>{i.rotulo}</span>
          </NavLink>
        ))}
        {noMais.length > 0 ? (
          <button
            type="button"
            className={`casca__inferior-link casca__mais${paginaNoMais ? ' casca__mais--atual' : ''}`}
            aria-haspopup="dialog"
            onClick={() => {
              definirMaisAberto(true);
            }}
          >
            <Icone nome={'mais' satisfies NomeIcone} />
            <span>Mais</span>
          </button>
        ) : null}
      </nav>
      <Modal
        titulo="Mais opções"
        aberto={maisAberto}
        onFechar={() => {
          definirMaisAberto(false);
        }}
      >
        <ul className="casca__lista">
          {noMais.map((i) => (
            <li key={i.para}>
              <NavLink
                to={i.para}
                className="casca__link"
                onClick={() => {
                  definirMaisAberto(false);
                }}
              >
                <Icone nome={i.icone} />
                <span>{i.rotulo}</span>
              </NavLink>
            </li>
          ))}
        </ul>
      </Modal>
    </div>
  );
}
