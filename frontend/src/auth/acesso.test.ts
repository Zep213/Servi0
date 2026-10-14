import { describe, expect, it } from 'vitest';
import { acessoNaPastoral } from './acesso';
import type { PastoralDoUsuario } from './destino';

const pastorais = (papel: PastoralDoUsuario['papel']): PastoralDoUsuario[] => [
  { id: 1, nome: 'Pascom', papel },
];

describe('acessoNaPastoral', () => {
  it('membro comum não vê gestão nem e-mails', () => {
    const a = acessoNaPastoral('SERVIDOR', pastorais('MEMBRO'), 1);
    expect(a.veGestao).toBe(false);
    expect(a.escala).toBe(false);
    expect(a.veEmail).toBe(false);
  });

  it('vice escala mas não força', () => {
    const a = acessoNaPastoral('SERVIDOR', pastorais('VICE'), 1);
    expect(a.escala).toBe(true);
    expect(a.forca).toBe(false);
    expect(a.coordena).toBe(false);
  });

  it('técnico de TI tem exatamente o acesso do vice', () => {
    expect(acessoNaPastoral('SERVIDOR', pastorais('TECNICO'), 1)).toEqual({
      ...acessoNaPastoral('SERVIDOR', pastorais('VICE'), 1),
      papel: 'TECNICO',
    });
  });

  it('redes sociais tem exatamente o acesso de membro', () => {
    expect(acessoNaPastoral('SERVIDOR', pastorais('REDES_SOCIAIS'), 1)).toEqual({
      ...acessoNaPastoral('SERVIDOR', pastorais('MEMBRO'), 1),
      papel: 'REDES_SOCIAIS',
    });
  });

  it('coordenador escala, força, vê e-mail e coordena; não lança financeiro', () => {
    const a = acessoNaPastoral('SERVIDOR', pastorais('COORDENADOR'), 1);
    expect(a).toMatchObject({
      escala: true,
      forca: true,
      veEmail: true,
      coordena: true,
      lancaFinanceiro: false,
    });
    expect(a.veFinanceiro).toBe(true);
  });

  it('tesoureiro lança e vê o financeiro, sem gerir a escala', () => {
    const a = acessoNaPastoral('SERVIDOR', pastorais('TESOUREIRO'), 1);
    expect(a).toMatchObject({
      lancaFinanceiro: true,
      veFinanceiro: true,
      escala: false,
      veGestao: true,
    });
  });

  it('padre age como coordenação em qualquer pastoral, mas não lança financeiro', () => {
    const a = acessoNaPastoral('PADRE', [], 1);
    expect(a).toMatchObject({
      veGestao: true,
      escala: true,
      forca: true,
      veEmail: true,
      lancaFinanceiro: false,
      veFinanceiro: true,
    });
  });

  it('pastoral sem papel e sem perfil alto: nada', () => {
    const a = acessoNaPastoral('SERVIDOR', pastorais('COORDENADOR'), 99);
    expect(a.veGestao).toBe(false);
    expect(a.papel).toBeNull();
  });
});
