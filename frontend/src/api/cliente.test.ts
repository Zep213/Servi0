import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ErroApi,
  clienteHttp,
  definirAoSessaoExpirada,
  garantirCsrf,
  lerCookie,
  reiniciarCsrf,
} from './cliente';

function resposta(corpo: unknown, status = 200): Response {
  if (status === 204) return new Response(null, { status });
  return new Response(JSON.stringify(corpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('cliente HTTP', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    reiniciarCsrf();
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/';
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    definirAoSessaoExpirada(null);
  });

  it('busca o CSRF uma única vez, mesmo com chamadas simultâneas', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
    await Promise.all([garantirCsrf(), garantirCsrf(), garantirCsrf()]);
    const chamadasCsrf = fetchMock.mock.calls.filter(([url]) => url === '/api/auth/csrf');
    expect(chamadasCsrf).toHaveLength(1);
  });

  it('envia X-XSRF-TOKEN em escrita, lido do cookie', async () => {
    document.cookie = 'XSRF-TOKEN=abc123; path=/';
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // csrf
    fetchMock.mockResolvedValueOnce(resposta({ ok: true }));

    await clienteHttp('/api/celebracoes', { method: 'POST', body: JSON.stringify({}) });

    const [, opcoes] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(new Headers(opcoes.headers).get('X-XSRF-TOKEN')).toBe('abc123');
    expect(opcoes.credentials).toBe('same-origin');
  });

  it('não manda CSRF em leitura', async () => {
    fetchMock.mockResolvedValueOnce(resposta([]));
    await clienteHttp('/api/me/escalas');
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [, opcoes] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(new Headers(opcoes.headers).has('X-XSRF-TOKEN')).toBe(false);
  });

  it('transforma o ProblemDetail em ErroApi com detalhe e erros por campo', async () => {
    fetchMock.mockResolvedValueOnce(
      resposta({ detail: 'Dados inválidos', erros: { email: 'já existe' } }, 400),
    );
    const erro = await clienteHttp('/api/usuarios').catch((e: unknown) => e);
    expect(erro).toBeInstanceOf(ErroApi);
    expect(erro).toMatchObject({
      status: 400,
      message: 'Dados inválidos',
      erros: { email: 'já existe' },
    });
  });

  it('usa mensagem genérica quando o erro não traz corpo', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>', { status: 502 }));
    const erro = await clienteHttp('/api/celebracoes').catch((e: unknown) => e);
    expect(erro).toMatchObject({ status: 502, message: 'Algo deu errado. Tente de novo.' });
  });

  it('401 em chamada autenticada avisa a sessão expirada', async () => {
    const aviso = vi.fn();
    definirAoSessaoExpirada(aviso);
    fetchMock.mockResolvedValueOnce(resposta({ detail: 'x' }, 401));
    await expect(clienteHttp('/api/me/escalas')).rejects.toBeInstanceOf(ErroApi);
    expect(aviso).toHaveBeenCalledOnce();
  });

  it('401 em /api/me não avisa sessão expirada (é só "não está logado")', async () => {
    const aviso = vi.fn();
    definirAoSessaoExpirada(aviso);
    fetchMock.mockResolvedValueOnce(resposta({ detail: 'x' }, 401));
    await expect(clienteHttp('/api/me')).rejects.toBeInstanceOf(ErroApi);
    expect(aviso).not.toHaveBeenCalled();
  });

  it('401 no login não dispara o aviso de sessão expirada', async () => {
    const aviso = vi.fn();
    definirAoSessaoExpirada(aviso);
    fetchMock.mockResolvedValueOnce(resposta({ detail: 'x' }, 401));
    await expect(
      clienteHttp('/api/auth/login', { method: 'POST', body: '{}' }),
    ).rejects.toBeInstanceOf(ErroApi);
    expect(aviso).not.toHaveBeenCalled();
  });

  it('204 devolve undefined', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 })); // csrf
    fetchMock.mockResolvedValueOnce(resposta(null, 204));
    await expect(clienteHttp('/api/auth/logout', { method: 'POST' })).resolves.toBeUndefined();
  });

  it('lerCookie devolve undefined quando o cookie não existe', () => {
    expect(lerCookie('NAO_EXISTE')).toBeUndefined();
  });
});
