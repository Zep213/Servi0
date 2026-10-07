import type { PastoralDoUsuario, PapelPastoral } from './destino';

/**
 * O que a pessoa pode ver e fazer em uma pastoral. O backend decide de verdade; aqui só se esconde
 * o que ela não pode usar. Espelha as regras do PastoraisPermissao e dos services:
 * - PADRE e ADMIN passam como coordenação em qualquer pastoral da paróquia;
 * - VICE escala e substitui, mas nunca força;
 * - só o tesoureiro (e o ADMIN) lança no financeiro; coordenação e PADRE só leem.
 */
export interface AcessoNaPastoral {
  papel: PapelPastoral | null;
  /** Vê a gestão: painel, escala da celebração, membros, reuniões, financeiro. */
  veGestao: boolean;
  /** Sorteia, escala e substitui. */
  escala: boolean;
  /** Escala alguém com impedimento. */
  forca: boolean;
  /** Vê o e-mail dos membros. */
  veEmail: boolean;
  /** Marca reunião e configura a pastoral. */
  coordena: boolean;
  /** Lança entradas e saídas. */
  lancaFinanceiro: boolean;
  /** Lê o financeiro. */
  veFinanceiro: boolean;
}

const SEM_ACESSO: AcessoNaPastoral = {
  papel: null,
  veGestao: false,
  escala: false,
  forca: false,
  veEmail: false,
  coordena: false,
  lancaFinanceiro: false,
  veFinanceiro: false,
};

export function acessoNaPastoral(
  perfil: string | undefined,
  pastorais: readonly PastoralDoUsuario[],
  pastoralId: number,
): AcessoNaPastoral {
  const papel = pastorais.find((p) => p.id === pastoralId)?.papel ?? null;
  const admin = perfil === 'ADMIN';
  const padre = perfil === 'PADRE';
  const coordenacao = admin || padre || papel === 'COORDENADOR';
  const ePapel = (...lista: PapelPastoral[]) => papel !== null && lista.includes(papel);

  if (!admin && !padre && papel === null) return SEM_ACESSO;
  return {
    papel,
    veGestao: admin || padre || ePapel('COORDENADOR', 'VICE', 'SECRETARIO', 'TESOUREIRO'),
    escala: admin || padre || ePapel('COORDENADOR', 'VICE'),
    forca: coordenacao,
    veEmail: coordenacao,
    coordena: coordenacao,
    lancaFinanceiro: admin || ePapel('TESOUREIRO'),
    veFinanceiro: admin || padre || ePapel('COORDENADOR', 'TESOUREIRO'),
  };
}
