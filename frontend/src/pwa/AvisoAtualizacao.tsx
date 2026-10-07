import { useRegisterSW } from 'virtual:pwa-register/react';
import { Botao } from '../componentes/Botao';

/** Avisa quando há uma versão nova do app no ar; só atualiza quando a pessoa pedir. */
export function AvisoAtualizacao() {
  const {
    needRefresh: [precisaAtualizar, definirPrecisaAtualizar],
    updateServiceWorker,
  } = useRegisterSW();

  if (!precisaAtualizar) return null;

  return (
    <div className="pwa-barra" role="status">
      <p>Nova versão disponível.</p>
      <div className="acoes-linha">
        <Botao
          onClick={() => {
            void updateServiceWorker(true);
          }}
        >
          Atualizar
        </Botao>
        <Botao
          variante="secundario"
          onClick={() => {
            definirPrecisaAtualizar(false);
          }}
        >
          Agora não
        </Botao>
      </div>
    </div>
  );
}
