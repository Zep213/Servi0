import { afterEach, describe, expect, it } from 'vitest';
import { CONTRASTE_MINIMO_TEXTO, razaoDeContraste } from './contraste';
import {
  CORES_CONHECIDAS,
  PALETA,
  aplicarCorDaPastoral,
  corDaPastoral,
  normalizarNome,
  textoSobre,
} from './pastorais';

const TOKENS_DE_TEXTO = ['#111827', '#5b6472', '#166534', '#854d0e', '#b91c1c'];

describe('contraste', () => {
  it('preto sobre branco dá 21:1', () => {
    expect(razaoDeContraste('#000000', '#ffffff')).toBeCloseTo(21, 0);
  });

  it('rejeita cor inválida', () => {
    expect(() => razaoDeContraste('azul', '#fff')).toThrow('Cor inválida');
  });
});

describe('textoSobre', () => {
  it('todas as cores do mapa e da paleta têm texto com pelo menos 4,5:1', () => {
    const cores = [...Object.values(CORES_CONHECIDAS), ...PALETA];
    for (const cor of cores) {
      const texto = textoSobre(cor.base);
      expect(razaoDeContraste(cor.base, texto), `cor ${cor.base}`).toBeGreaterThanOrEqual(
        CONTRASTE_MINIMO_TEXTO,
      );
    }
  });

  it('escolhe branco em fundo escuro e quase preto em fundo claro', () => {
    expect(textoSobre('#1d4ed8')).toBe('#ffffff');
    expect(textoSobre('#fde68a')).toBe('#111827');
  });
});

describe('cor da pastoral', () => {
  it('normaliza nome: sem acento e sem caixa', () => {
    expect(normalizarNome('  Páscom ')).toBe('pascom');
    expect(normalizarNome('PASCOM')).toBe('pascom');
  });

  it('pascom e ecc têm as cores do mapa, sem depender do id', () => {
    expect(corDaPastoral('Pascom', 1)).toBe(CORES_CONHECIDAS['pascom']);
    expect(corDaPastoral('ECC', 999)).toBe(CORES_CONHECIDAS['ecc']);
  });

  it('pastoral fora do mapa pega a mesma cor da paleta para o mesmo id', () => {
    const primeira = corDaPastoral('Liturgia', 7);
    expect(corDaPastoral('Liturgia', 7)).toBe(primeira);
    expect(PALETA).toContain(primeira);
  });

  it('a paleta inteira é usada pelos ids (sem cor que nunca aparece)', () => {
    const usadas = new Set<string>();
    for (let id = 0; id < PALETA.length * 3; id++) {
      usadas.add(corDaPastoral('Outra', id).base);
    }
    expect(usadas.size).toBe(PALETA.length);
  });

  it('cores de texto dos tokens têm contraste sobre o fundo claro', () => {
    for (const texto of TOKENS_DE_TEXTO) {
      expect(razaoDeContraste(texto, '#ffffff'), texto).toBeGreaterThanOrEqual(
        CONTRASTE_MINIMO_TEXTO,
      );
    }
  });
});

describe('aplicarCorDaPastoral', () => {
  afterEach(() => {
    document.documentElement.removeAttribute('style');
  });

  it('define as variáveis no :root', () => {
    aplicarCorDaPastoral(CORES_CONHECIDAS['ecc'] as (typeof PALETA)[number]);
    const raiz = document.documentElement.style;
    expect(raiz.getPropertyValue('--accent')).toBe('#b91c1c');
    expect(raiz.getPropertyValue('--accent-soft')).toBe('#fee2e2');
    expect(raiz.getPropertyValue('--accent-text')).toBe('#ffffff');
  });
});
