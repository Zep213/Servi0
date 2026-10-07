/**
 * Cliente HTTP mínimo pra semear dados direto na API (sem passar pela UI), do jeito que o
 * `cliente.ts` do app faz: cookie XSRF-TOKEN lido de `GET /api/auth/csrf` e devolvido no
 * cabeçalho X-XSRF-TOKEN em toda escrita. Login é form-urlencoded, não JSON.
 */
export class ClienteApi {
  private readonly cookies = new Map<string, string>();

  constructor(private readonly baseUrl: string) {}

  private guardarCookies(resposta: Response): void {
    for (const linha of resposta.headers.getSetCookie()) {
      const par = linha.split(';', 1)[0] ?? '';
      const indice = par.indexOf('=');
      if (indice <= 0) continue;
      this.cookies.set(par.slice(0, indice).trim(), par.slice(indice + 1).trim());
    }
  }

  private cabecalhoCookie(): string {
    return [...this.cookies.entries()].map(([nome, valor]) => `${nome}=${valor}`).join('; ');
  }

  async csrf(): Promise<void> {
    const resposta = await fetch(`${this.baseUrl}/api/auth/csrf`);
    this.guardarCookies(resposta);
  }

  async login(email: string, senha: string): Promise<void> {
    await this.csrf();
    const resposta = await fetch(`${this.baseUrl}/api/auth/login`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'X-XSRF-TOKEN': this.cookies.get('XSRF-TOKEN') ?? '',
        Cookie: this.cabecalhoCookie(),
      },
      body: new URLSearchParams({ email, senha }),
      redirect: 'manual',
    });
    this.guardarCookies(resposta);
    if (resposta.status >= 400) {
      throw new Error(`Login de ${email} falhou (status ${String(resposta.status)})`);
    }
  }

  async post<T>(caminho: string, corpo: unknown): Promise<T> {
    return this.enviar<T>('POST', caminho, corpo);
  }

  private async enviar<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
    const resposta = await fetch(`${this.baseUrl}${caminho}`, {
      method: metodo,
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': this.cookies.get('XSRF-TOKEN') ?? '',
        Cookie: this.cabecalhoCookie(),
      },
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
    });
    this.guardarCookies(resposta);
    if (!resposta.ok) {
      const texto = await resposta.text();
      throw new Error(`${metodo} ${caminho} → ${String(resposta.status)}: ${texto}`);
    }
    if (resposta.status === 204) return undefined as T;
    return (await resposta.json()) as T;
  }
}
