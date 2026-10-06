interface CardEstatisticaProps {
  rotulo: string;
  valor: number | string;
  detalhe?: string;
}

/** Número grande com rótulo em texto simples (o valor é lido por leitor de tela junto com o rótulo). */
export function CardEstatistica({ rotulo, valor, detalhe }: CardEstatisticaProps) {
  return (
    <div className="card-estatistica">
      <p className="card-estatistica__valor">{valor}</p>
      <p className="card-estatistica__rotulo">{rotulo}</p>
      {detalhe ? <p className="card-estatistica__detalhe">{detalhe}</p> : null}
    </div>
  );
}
