import type { ReactNode } from 'react';

interface EstadoVazioProps {
  titulo: string;
  texto?: string;
  acao?: ReactNode;
}

export function EstadoVazio({ titulo, texto, acao }: EstadoVazioProps) {
  return (
    <div className="estado-vazio" role="status">
      <p className="estado-vazio__titulo">{titulo}</p>
      {texto ? <p className="texto-corpo">{texto}</p> : null}
      {acao}
    </div>
  );
}
