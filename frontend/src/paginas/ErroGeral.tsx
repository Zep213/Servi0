import { EstadoVazio } from '../componentes/EstadoVazio';

/** Erro inesperado: texto simples, sem detalhe técnico. */
export function ErroGeral() {
  return (
    <EstadoVazio
      titulo="Algo deu errado"
      texto="Tente atualizar a página. Se o problema continuar, avise a coordenação."
      acao={
        <button
          type="button"
          className="botao botao--primario"
          onClick={() => {
            window.location.reload();
          }}
        >
          Atualizar a página
        </button>
      }
    />
  );
}
