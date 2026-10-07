/** Placeholder de carregamento. Anuncia "carregando" uma vez, e não a cada bloco. */
export function Esqueleto({ linhas = 3 }: { linhas?: number }) {
  return (
    <div className="esqueleto" role="status" aria-label="Carregando">
      {Array.from({ length: linhas }, (_, i) => (
        <div key={i} className="esqueleto__linha" aria-hidden="true" />
      ))}
    </div>
  );
}
