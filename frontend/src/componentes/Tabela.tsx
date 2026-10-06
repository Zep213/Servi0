import type { ReactNode } from 'react';

export interface ColunaTabela<T> {
  chave: string;
  titulo: string;
  celula: (linha: T) => ReactNode;
}

interface TabelaProps<T> {
  titulo: string;
  colunas: ColunaTabela<T>[];
  linhas: T[];
  chaveDaLinha: (linha: T) => string | number;
}

/**
 * Tabela no computador; no celular cada linha vira um cartão, com o nome da coluna em cada campo
 * (data-label), pelo CSS. Mesma marcação semântica nos dois.
 */
export function Tabela<T>({ titulo, colunas, linhas, chaveDaLinha }: TabelaProps<T>) {
  return (
    <div className="tabela-responsiva">
      <table className="tabela" aria-label={titulo}>
        <thead>
          <tr>
            {colunas.map((c) => (
              <th key={c.chave} scope="col">
                {c.titulo}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {linhas.map((linha) => (
            <tr key={chaveDaLinha(linha)}>
              {colunas.map((c) => (
                <td key={c.chave} data-label={c.titulo}>
                  {c.celula(linha)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
