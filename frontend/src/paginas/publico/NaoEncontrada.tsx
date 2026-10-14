import { Link } from 'react-router';

export function NaoEncontrada() {
  return (
    <main className="entrada">
      <h1>Página não encontrada</h1>
      <p className="texto-corpo">O endereço pode estar errado ou a página foi mudada de lugar.</p>
      <Link to="/" className="botao botao--primario">
        Ir para o início
      </Link>
    </main>
  );
}
