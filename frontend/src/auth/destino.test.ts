import { describe, expect, it, vi } from 'vitest';
import {
  destinoInicial,
  lerPastoralSalva,
  papelNaPastoral,
  salvarPastoral,
  type PastoralDoUsuario,
} from './destino';

const pascomComoMembro: PastoralDoUsuario = { id: 1, nome: 'Pascom', papel: 'MEMBRO' };
const pascomComoCoordenador: PastoralDoUsuario = { id: 1, nome: 'Pascom', papel: 'COORDENADOR' };
const eccComoVice: PastoralDoUsuario = { id: 2, nome: 'ECC', papel: 'VICE' };

describe('destinoInicial', () => {
  it('MEMBRO vai às próprias escalas', () => {
    expect(destinoInicial({ perfil: 'SERVIDOR' }, [pascomComoMembro], 1)).toBe('/minhas-escalas');
  });

  it('quem tem papel de gestão na pastoral ativa vai ao painel dela', () => {
    expect(destinoInicial({ perfil: 'SERVIDOR' }, [pascomComoCoordenador], 1)).toBe(
      '/pastoral/1/painel',
    );
    expect(destinoInicial({ perfil: 'SERVIDOR' }, [pascomComoMembro, eccComoVice], 2)).toBe(
      '/pastoral/2/painel',
    );
  });

  it('PADRE e ADMIN sem papel vão às celebrações', () => {
    expect(destinoInicial({ perfil: 'PADRE' }, [], null)).toBe('/celebracoes');
    expect(destinoInicial({ perfil: 'ADMIN' }, [], null)).toBe('/celebracoes');
  });

  it('pastoral ativa inexistente cai na primeira', () => {
    expect(destinoInicial({ perfil: 'SERVIDOR' }, [pascomComoCoordenador], 99)).toBe(
      '/pastoral/1/painel',
    );
  });
});

describe('papelNaPastoral', () => {
  it('devolve o papel da pastoral ativa, ou null se não participa', () => {
    expect(papelNaPastoral([pascomComoCoordenador], 1)).toBe('COORDENADOR');
    expect(papelNaPastoral([pascomComoCoordenador], 2)).toBeNull();
  });
});

describe('pastoral salva', () => {
  it('guarda e lê a escolha', () => {
    salvarPastoral(3);
    expect(lerPastoralSalva()).toBe(3);
  });

  it('valor inválido no armazenamento vira null', () => {
    window.localStorage.setItem('servio.pastoralAtiva', 'abc');
    expect(lerPastoralSalva()).toBeNull();
  });

  it('localStorage que falha não derruba a aplicação', () => {
    const espiao = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    try {
      expect(lerPastoralSalva()).toBeNull();
    } finally {
      espiao.mockRestore();
    }
  });
});
