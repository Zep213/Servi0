import { useEffect, useId, useRef, type ReactNode } from 'react';
import { Botao } from './Botao';
import { Icone } from './Icone';

interface ModalProps {
  titulo: string;
  aberto: boolean;
  onFechar: () => void;
  children: ReactNode;
}

const FOCAVEIS = 'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])';

/**
 * Janela de diálogo. No celular vira folha que sobe de baixo (CSS). Fecha com Escape ou pelo
 * botão; o foco entra no diálogo ao abrir e volta para quem o abriu ao fechar.
 */
export function Modal({ titulo, aberto, onFechar, children }: ModalProps) {
  const id = useId();
  const dialogo = useRef<HTMLDivElement>(null);
  const origem = useRef<HTMLElement | null>(null);

  useEffect(() => {
    if (!aberto) return;
    origem.current = document.activeElement as HTMLElement | null;
    const primeiro = dialogo.current?.querySelector<HTMLElement>(FOCAVEIS);
    (primeiro ?? dialogo.current)?.focus();
    const aoTeclar = (evento: KeyboardEvent) => {
      if (evento.key === 'Escape') onFechar();
    };
    document.addEventListener('keydown', aoTeclar);
    return () => {
      document.removeEventListener('keydown', aoTeclar);
      origem.current?.focus();
    };
  }, [aberto, onFechar]);

  if (!aberto) return null;
  return (
    <div
      className="modal-fundo"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onFechar();
      }}
    >
      <div
        ref={dialogo}
        className="modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby={`${id}-titulo`}
        tabIndex={-1}
      >
        <header className="modal__cabecalho">
          <h2 id={`${id}-titulo`} className="modal__titulo">
            {titulo}
          </h2>
          <Botao variante="secundario" onClick={onFechar} aria-label="Fechar">
            <Icone nome="fechar" />
          </Botao>
        </header>
        <div className="modal__corpo">{children}</div>
      </div>
    </div>
  );
}
