/**
 * Cliente HTTP único da aplicação (o `mutator` do Orval). Regras:
 * - mesma origem (o proxy do Vite e o nginx mantêm /api no mesmo domínio);
 * - em escrita (POST/PUT/PATCH/DELETE), manda o cabeçalho X-XSRF-TOKEN lido do cookie XSRF-TOKEN;
 * - o CSRF é buscado uma vez, em GET /api/auth/csrf, antes da primeira chamada;
 * - erro vira ErroApi com status, detalhe e erros por campo (do ProblemDetail do backend);
 * - 401 numa chamada autenticada avisa a aplicação, que limpa o cache e leva ao login.
 */

export class ErroApi extends Error {
  readonly status: number;
  readonly erros: Readonly<Record<string, string>>;

  constructor(status: number, mensagem: string, erros: Record<string, string> = {}) {
    super(mensagem);
    this.name = 'ErroApi';
    this.status = status;
    this.erros = erros;
  }
}

const METODOS_DE_ESCRITA = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);
const ROTAS_SEM_SESSAO = new Set(['/api/auth/login', '/api/auth/csrf']);

let aoSessaoExpirada: (() => void) | null = null;
let csrfCarregado: Promise<void> | null = null;

/** A aplicação registra o que fazer quando a sessão cai (limpar cache e ir ao login). */
export function definirAoSessaoExpirada(acao: (() => void) | null): void {
  aoSessaoExpirada = acao;
}

/** Zera o estado do CSRF (usado pelos testes e depois de sair). */
export function reiniciarCsrf(): void {
  csrfCarregado = null;
}

export function lerCookie(nome: string): string | undefined {
  const prefixo = `${nome}=`;
  const achado = document.cookie.split('; ').find((c) => c.startsWith(prefixo));
  return achado ? decodeURIComponent(achado.slice(prefixo.length)) : undefined;
}

/** Garante o cookie XSRF-TOKEN: uma chamada, compartilhada por todas as que vierem depois. */
export function garantirCsrf(): Promise<void> {
  if (!csrfCarregado) {
    csrfCarregado = fetch('/api/auth/csrf', { credentials: 'same-origin' }).then((resposta) => {
      if (!resposta.ok) {
        csrfCarregado = null;
        throw new ErroApi(resposta.status, 'Não foi possível preparar a conexão');
      }
    });
  }
  return csrfCarregado;
}

async function lerErro(resposta: Response): Promise<ErroApi> {
  let corpo: unknown = null;
  try {
    corpo = await resposta.json();
  } catch {
    corpo = null;
  }
  const obj = (corpo ?? {}) as { detail?: unknown; erros?: unknown };
  const detalhe = typeof obj.detail === 'string' ? obj.detail : 'Algo deu errado. Tente de novo.';
  const erros: Record<string, string> = {};
  if (obj.erros && typeof obj.erros === 'object') {
    for (const [campo, msg] of Object.entries(obj.erros as Record<string, unknown>)) {
      if (typeof msg === 'string') erros[campo] = msg;
    }
  }
  return new ErroApi(resposta.status, detalhe, erros);
}

export async function clienteHttp<T>(url: string, opcoes: RequestInit = {}): Promise<T> {
  const metodo = (opcoes.method ?? 'GET').toUpperCase();
  const cabecalhos = new Headers(opcoes.headers);
  if (opcoes.body !== undefined && !cabecalhos.has('Content-Type')) {
    cabecalhos.set('Content-Type', 'application/json');
  }
  if (METODOS_DE_ESCRITA.has(metodo)) {
    await garantirCsrf();
    const token = lerCookie('XSRF-TOKEN');
    if (token) cabecalhos.set('X-XSRF-TOKEN', token);
  }
  cabecalhos.set('Accept', 'application/json');

  const resposta = await fetch(url, {
    ...opcoes,
    method: metodo,
    headers: cabecalhos,
    credentials: 'same-origin',
  });

  if (resposta.status === 401 && !ROTAS_SEM_SESSAO.has(new URL(url, 'http://local').pathname)) {
    aoSessaoExpirada?.();
  }
  if (!resposta.ok) {
    throw await lerErro(resposta);
  }
  if (resposta.status === 204) {
    return undefined as T;
  }
  return (await resposta.json()) as T;
}
