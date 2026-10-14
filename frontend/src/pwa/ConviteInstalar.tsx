import { useEffect, useState } from 'react';
import { useSessao } from '../auth/sessaoContexto';
import { Botao } from '../componentes/Botao';

/** O navegador dispara isso quando o app pode ser instalado; não está no lib.dom ainda. */
interface EventoInstalar extends Event {
  prompt: () => Promise<void>;
}

const CHAVE_DISPENSADO = 'servio.instalarDispensado';

function dispensadoAntes(): boolean {
  try {
    return window.localStorage.getItem(CHAVE_DISPENSADO) === '1';
  } catch {
    return false;
  }
}

function lembrarDispensado(): void {
  try {
    window.localStorage.setItem(CHAVE_DISPENSADO, '1');
  } catch {
    // sem persistência: pergunta de novo na próxima sessão
  }
}

/** Convite para instalar o app, só depois do primeiro login e só se o navegador oferecer. */
export function ConviteInstalar() {
  const { usuario } = useSessao();
  const [evento, definirEvento] = useState<EventoInstalar | null>(null);
  const [dispensado, definirDispensado] = useState(dispensadoAntes);

  useEffect(() => {
    const aoPoderInstalar = (e: Event) => {
      e.preventDefault();
      definirEvento(e as EventoInstalar);
    };
    window.addEventListener('beforeinstallprompt', aoPoderInstalar);
    return () => {
      window.removeEventListener('beforeinstallprompt', aoPoderInstalar);
    };
  }, []);

  if (!usuario || !evento || dispensado) return null;

  return (
    <div className="pwa-barra" role="status">
      <p>Instalar o Servio neste aparelho?</p>
      <div className="acoes-linha">
        <Botao
          onClick={() => {
            void evento.prompt();
            definirEvento(null);
          }}
        >
          Instalar
        </Botao>
        <Botao
          variante="secundario"
          onClick={() => {
            lembrarDispensado();
            definirDispensado(true);
          }}
        >
          Agora não
        </Botao>
      </div>
    </div>
  );
}
