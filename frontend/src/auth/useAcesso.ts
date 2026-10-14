import { useParams } from 'react-router';
import { acessoNaPastoral, type AcessoNaPastoral } from './acesso';
import { useSessao } from './sessaoContexto';

/** Acesso da pessoa logada na pastoral da rota (:pastoralId). */
export function useAcessoDaPastoral(): { pastoralId: number; acesso: AcessoNaPastoral } {
  const { pastoralId } = useParams();
  const { usuario, pastorais } = useSessao();
  const id = Number(pastoralId);
  return { pastoralId: id, acesso: acessoNaPastoral(usuario?.perfil ?? undefined, pastorais, id) };
}
