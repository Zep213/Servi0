import { useId, type InputHTMLAttributes, type ReactNode } from 'react';

interface CampoProps extends InputHTMLAttributes<HTMLInputElement> {
  rotulo: string;
  erro?: string;
  ajuda?: ReactNode;
}

/** Rótulo visível, erro ligado ao campo (aria-describedby) e alvo de toque de 44px. */
export function Campo({ rotulo, erro, ajuda, id, ...resto }: CampoProps) {
  const gerado = useId();
  const idCampo = id ?? gerado;
  const idErro = `${idCampo}-erro`;
  const idAjuda = `${idCampo}-ajuda`;
  const descricoes =
    [erro ? idErro : null, ajuda ? idAjuda : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className={`campo${erro ? ' campo--erro' : ''}`}>
      <label htmlFor={idCampo} className="campo__rotulo">
        {rotulo}
      </label>
      <input
        id={idCampo}
        className="campo__input"
        aria-invalid={erro ? true : undefined}
        aria-describedby={descricoes}
        {...resto}
      />
      {ajuda ? (
        <p id={idAjuda} className="campo__ajuda">
          {ajuda}
        </p>
      ) : null}
      {erro ? (
        <p id={idErro} className="campo__erro">
          {erro}
        </p>
      ) : null}
    </div>
  );
}
