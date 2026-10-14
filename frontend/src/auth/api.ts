import { clienteHttp, ErroApi } from '../api/cliente';

/**
 * Login e logout. Não estão no OpenAPI (são rotas do formLogin do Spring Security), por isso
 * ficam aqui e não no cliente gerado. Login de sucesso responde 204.
 */
export async function entrar(email: string, senha: string): Promise<void> {
  await clienteHttp<undefined>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ email, senha }).toString(),
  });
}

export async function sair(): Promise<void> {
  await clienteHttp<undefined>('/api/auth/logout', { method: 'POST' });
}

/** Texto para a pessoa, por código de erro. Nunca mostra detalhe técnico. */
export function mensagemDeErroDeLogin(erro: unknown): string {
  if (erro instanceof ErroApi) {
    if (erro.status === 401) return 'E-mail ou senha inválidos';
    if (erro.status === 429) return 'Muitas tentativas, aguarde alguns minutos';
  }
  return 'Não foi possível entrar agora. Verifique sua conexão e tente de novo.';
}
