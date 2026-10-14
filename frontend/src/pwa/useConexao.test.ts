import { act, renderHook } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { useConexao } from './useConexao';

function definirOnline(valor: boolean) {
  Object.defineProperty(window.navigator, 'onLine', { value: valor, configurable: true });
}

describe('useConexao', () => {
  afterEach(() => {
    definirOnline(true);
  });

  it('começa com o valor atual do navegador e muda com online/offline', () => {
    definirOnline(true);
    const { result } = renderHook(() => useConexao());
    expect(result.current).toBe(true);

    act(() => {
      definirOnline(false);
      window.dispatchEvent(new Event('offline'));
    });
    expect(result.current).toBe(false);

    act(() => {
      definirOnline(true);
      window.dispatchEvent(new Event('online'));
    });
    expect(result.current).toBe(true);
  });
});
