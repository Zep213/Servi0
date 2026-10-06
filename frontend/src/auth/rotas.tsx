import { Link, Navigate, Outlet, useLocation } from 'react-router';
import { EstadoVazio } from '../componentes/EstadoVazio';
import { Esqueleto } from '../componentes/Esqueleto';
import { destinoInicial, PAPEIS_DE_GESTAO } from './destino';
import { useSessao } from './sessaoContexto';

/** Só entra quem tem sessão. Sem sessão, vai ao login e lembra de onde veio. */
export function ExigeSessao() {
  const { usuario, carregando } = useSessao();
  const local = useLocation();
  if (carregando) return <Esqueleto linhas={4} />;
  if (!usuario) {
    return <Navigate to="/entrar" replace state={{ de: `${local.pathname}${local.search}` }} />;
  }
  return <Outlet />;
}

/** Restringe por perfil global (PADRE, ADMIN). Quem não tem o perfil vê uma tela de acesso, não uma falha. */
export function ExigePerfil({ perfis }: { perfis: readonly string[] }) {
  const { usuario } = useSessao();
  if (!usuario) return null;
  if (!perfis.includes(usuario.perfil ?? '')) return <SemAcesso />;
  return <Outlet />;
}

/**
 * Restringe por papel na pastoral ativa. PADRE e ADMIN passam, como no backend (PADRE age como
 * coordenador em qualquer pastoral da própria paróquia).
 */
export function ExigePapelDeGestao() {
  const { usuario, papelAtivo } = useSessao();
  if (!usuario) return null;
  const podeGerir =
    usuario.perfil === 'ADMIN' ||
    usuario.perfil === 'PADRE' ||
    (papelAtivo !== null && PAPEIS_DE_GESTAO.includes(papelAtivo));
  return podeGerir ? <Outlet /> : <SemAcesso />;
}

export function RedirecionaInicio() {
  const { usuario, pastorais, pastoralAtiva } = useSessao();
  if (!usuario) return null;
  return <Navigate to={destinoInicial(usuario, pastorais, pastoralAtiva?.id ?? null)} replace />;
}

function SemAcesso() {
  return (
    <EstadoVazio
      titulo="Esta área não está disponível para o seu acesso"
      texto="Se você acha que deveria ver esta página, fale com a coordenação da pastoral."
      acao={
        <Link to="/" className="botao botao--secundario">
          Voltar ao início
        </Link>
      }
    />
  );
}
