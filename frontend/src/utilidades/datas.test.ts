import { describe, expect, it } from 'vitest';
import { dataPorExtenso, horaCurta, hojeIso, prazoLegivel, primeiroNome, somarDias } from './datas';

describe('datas em português', () => {
  it('data por extenso com dia da semana', () => {
    // 2 de março de 2031 é um domingo
    expect(dataPorExtenso('2031-03-02')).toBe('domingo, 2 de março de 2031');
  });

  it('data inválida volta como veio, e vazia vira vazia', () => {
    expect(dataPorExtenso('não é data')).toBe('não é data');
    expect(dataPorExtenso(undefined)).toBe('');
  });

  it('hora sem segundos', () => {
    expect(horaCurta('18:30:00')).toBe('18:30');
    expect(horaCurta(undefined)).toBe('');
  });

  it('prazo com dia, mês e horário, sem fuso', () => {
    expect(prazoLegivel('2026-10-08T18:00:00')).toBe('8 de outubro às 18:00');
    expect(prazoLegivel('2026-01-02T07:05:00')).toBe('2 de janeiro às 07:05');
  });

  it('primeiro nome da saudação', () => {
    expect(primeiroNome('  Maria Clara  Souza ')).toBe('Maria');
    expect(primeiroNome('')).toBe('');
    expect(primeiroNome(undefined)).toBe('');
  });
});

describe('período', () => {
  it('hoje em AAAA-MM-DD, no horário local', () => {
    expect(hojeIso(new Date(2031, 2, 2, 23, 30))).toBe('2031-03-02');
  });

  it('somar dias atravessa mês e ano', () => {
    expect(somarDias('2031-03-02', 365)).toBe('2032-03-01');
    expect(somarDias('2031-01-01', -1)).toBe('2030-12-31');
  });
});
