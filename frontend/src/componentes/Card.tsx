import type { ReactNode } from 'react';

interface CardProps {
  titulo?: string;
  acoes?: ReactNode;
  children: ReactNode;
}

export function Card({ titulo, acoes, children }: CardProps) {
  return (
    <section className="card">
      {titulo || acoes ? (
        <header className="card__cabecalho">
          {titulo ? <h2 className="card__titulo">{titulo}</h2> : null}
          {acoes ? <div className="card__acoes">{acoes}</div> : null}
        </header>
      ) : null}
      {children}
    </section>
  );
}
