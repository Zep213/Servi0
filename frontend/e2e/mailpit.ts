const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://localhost:8025';

interface MensagemResumo {
  ID: string;
}

interface BuscaMailpit {
  messages: MensagemResumo[];
}

interface MensagemDetalhe {
  Text?: string;
  HTML?: string;
}

/** Apaga tudo na caixa do Mailpit: cada teste começa com a caixa vazia. */
export async function limparCaixaDeEntrada(): Promise<void> {
  await fetch(`${MAILPIT_URL}/api/v1/messages`, { method: 'DELETE' });
}

/** Espera chegar um e-mail para o endereço e devolve o corpo (texto simples). */
export async function esperarEmailPara(
  destinatario: string,
  { tentativas = 30, intervaloMs = 1000 }: { tentativas?: number; intervaloMs?: number } = {},
): Promise<string> {
  for (let i = 0; i < tentativas; i++) {
    const busca = (await (
      await fetch(`${MAILPIT_URL}/api/v1/search?query=${encodeURIComponent(`to:${destinatario}`)}`)
    ).json()) as BuscaMailpit;
    if (busca.messages.length > 0) {
      const detalhe = (await (
        await fetch(`${MAILPIT_URL}/api/v1/message/${busca.messages[0].ID}`)
      ).json()) as MensagemDetalhe;
      return detalhe.Text ?? detalhe.HTML ?? '';
    }
    await new Promise((resolver) => setTimeout(resolver, intervaloMs));
  }
  throw new Error(`Nenhum e-mail chegou para ${destinatario} a tempo`);
}

/** Tira o token do link "/convite#token" do corpo do e-mail de convite. */
export function tokenDoConvite(corpoDoEmail: string): string {
  const encontrado = /\/convite#(\S+)/.exec(corpoDoEmail);
  if (!encontrado?.[1]) throw new Error('Link do convite não encontrado no e-mail');
  return encontrado[1];
}
