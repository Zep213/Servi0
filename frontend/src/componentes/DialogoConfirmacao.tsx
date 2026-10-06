import { Botao } from './Botao';
import { Modal } from './Modal';

interface DialogoConfirmacaoProps {
  aberto: boolean;
  titulo: string;
  mensagem: string;
  textoConfirmar: string;
  perigoso?: boolean;
  onConfirmar: () => void;
  onCancelar: () => void;
}

/** Pergunta antes de uma ação que não dá para desfazer; a mensagem diz o que vai acontecer. */
export function DialogoConfirmacao({
  aberto,
  titulo,
  mensagem,
  textoConfirmar,
  perigoso,
  onConfirmar,
  onCancelar,
}: DialogoConfirmacaoProps) {
  return (
    <Modal titulo={titulo} aberto={aberto} onFechar={onCancelar}>
      <p className="texto-corpo">{mensagem}</p>
      <div className="acoes-modal">
        <Botao variante="secundario" onClick={onCancelar}>
          Voltar
        </Botao>
        <Botao variante={perigoso ? 'perigo' : 'primario'} onClick={onConfirmar}>
          {textoConfirmar}
        </Botao>
      </div>
    </Modal>
  );
}
