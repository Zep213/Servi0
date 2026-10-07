import { Link } from 'react-router';
import { EstadoVazio } from '../../componentes/EstadoVazio';

export function NaoEncontrada() {
  return (
    <EstadoVazio
      titulo="Página não encontrada"
      texto="O endereço pode estar errado ou a página foi mudada de lugar."
      acao={
        <Link to="/" className="botao botao--primario">
          Ir para o início
        </Link>
      }
    />
  );
}
