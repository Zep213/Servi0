import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { expect, test } from '@playwright/test';
import type { DadosSemeados } from './global-setup';
import type { Page } from '@playwright/test';
import { esperarEmailPara, limparCaixaDeEntrada, linkDoConvite } from './mailpit';

const ARQUIVO_SEMEADURA = path.join(import.meta.dirname, '..', 'test-results', 'semeadura.json');

async function lerDadosSemeados(): Promise<DadosSemeados> {
  return JSON.parse(await readFile(ARQUIVO_SEMEADURA, 'utf-8')) as DadosSemeados;
}

/** O painel abre sempre no mês de hoje; "próximo domingo" pode cair no mês seguinte. */
async function irParaOMesDaCelebracao(page: Page, celebracaoData: string): Promise<void> {
  const mesDaCelebracao = celebracaoData.slice(0, 7);
  const mesDeHoje = new Date().toISOString().slice(0, 7);
  if (mesDaCelebracao !== mesDeHoje) {
    await page.getByRole('button', { name: 'Próximo mês' }).click();
  }
}

/**
 * Ponta a ponta: a coordenadora entra, sorteia a missa de domingo; o convite chega por e-mail
 * (lido via API do Mailpit); a pessoa sorteada abre o link do e-mail e confirma; o painel passa a mostrar a
 * confirmação. Dados de partida vêm do `global-setup.ts` (semeados pela API, sem passar pela UI).
 */
test('coordenadora sorteia, servidora confirma pelo e-mail, painel mostra a confirmação', async ({
  page,
  context,
}) => {
  const dados = await lerDadosSemeados();
  await limparCaixaDeEntrada();

  await page.goto('/entrar');
  await page.getByLabel('E-mail').fill(dados.coordenador.email);
  await page.getByLabel('Senha').fill(dados.coordenador.senha);
  await page.getByRole('button', { name: 'Entrar' }).click();

  await expect(page).toHaveURL(new RegExp(`/pastoral/${String(dados.pastoralId)}/painel`));
  await irParaOMesDaCelebracao(page, dados.celebracaoData);
  await page.getByRole('button', { name: 'Sortear' }).click();
  await expect(page.getByText(/Sorteio feito/)).toBeVisible();

  const corpoDoEmail = await esperarEmailPara(dados.servidor.email);

  const paginaDoConvite = await context.newPage();
  await paginaDoConvite.goto(linkDoConvite(corpoDoEmail));
  await expect(paginaDoConvite.getByText(/Olá,/)).toBeVisible();
  await paginaDoConvite.getByRole('button', { name: 'Confirmo minha presença' }).click();
  await expect(paginaDoConvite.getByText('Presença confirmada')).toBeVisible();
  await paginaDoConvite.close();

  await page.reload();
  await irParaOMesDaCelebracao(page, dados.celebracaoData);
  await expect(page.getByText('Completo', { exact: true })).toBeVisible();
});
