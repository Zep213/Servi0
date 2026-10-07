import 'vitest';

interface CorrespondentesDeAcessibilidade<R = unknown> {
  toHaveNoViolations: () => R;
}

declare module 'vitest' {
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- só estende o matcher
  interface Assertion<T = unknown> extends CorrespondentesDeAcessibilidade<T> {}
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- só estende o matcher
  interface AsymmetricMatchersContaining extends CorrespondentesDeAcessibilidade {}
}
