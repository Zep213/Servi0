import { Navigate, useLocation, useNavigate } from 'react-router';
import { destinoInicial, NOME_DO_PAPEL } from '../../auth/destino';
import { useSessao } from '../../auth/sessaoContexto';
import { Botao } from '../../componentes/Botao';

/** Depois do login, quem participa de mais de uma pastoral escolhe em qual vai trabalhar. */
export function EscolherPastoral() {
  const { usuario, pastorais, trocarPastoral } = useSessao();
  const navegar = useNavigate();
  const de = (useLocation().state as { de?: string } | null)?.de;

  if (!usuario) return null;
  if (pastorais.length <= 1) {
    return <Navigate to={de ?? destinoInicial(usuario, pastorais, null)} replace />;
  }

  return (
    <main className="entrada">
      <h1>Em qual pastoral você vai trabalhar agora?</h1>
      <p className="texto-corpo">Dá para trocar depois, no topo da tela.</p>
      <ul className="escolha-pastoral">
        {pastorais.map((p) => (
          <li key={p.id}>
            <Botao
              variante="secundario"
              className="escolha-pastoral__botao"
              onClick={() => {
                trocarPastoral(p.id);
                void navegar(de ?? destinoInicial(usuario, pastorais, p.id), { replace: true });
              }}
            >
              <span className="escolha-pastoral__nome">{p.nome}</span>
              <span className="escolha-pastoral__papel">{NOME_DO_PAPEL[p.papel]}</span>
            </Botao>
          </li>
        ))}
      </ul>
    </main>
  );
}
