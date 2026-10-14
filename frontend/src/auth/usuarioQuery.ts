import { getGetMeQueryKey, getMe } from '../api/generated/servio';

/** Consulta de quem está logado. Usada pelo provedor de sessão e pelo login, com a mesma chave. */
export const usuarioQuery = {
  queryKey: getGetMeQueryKey(),
  queryFn: async () => (await getMe()).data,
  retry: false,
  staleTime: 5 * 60 * 1000,
};
