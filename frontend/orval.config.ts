import { defineConfig } from 'orval';

// Cliente gerado a partir do OpenAPI do backend (npm run gen:api, com o backend ligado e OPENAPI_ENABLED=true).
// O código gerado é commitado: o build não depende do backend no ar.
const capitalizar = (texto: string) => texto.charAt(0).toUpperCase() + texto.slice(1);

/**
 * Nome da operação a partir do verbo e do caminho, com os parâmetros no nome:
 * GET /api/pastorais/{id}/membros vira getPastoraisIdMembros. Os nomes do springdoc (buscar_1...)
 * não dizem nada a quem lê o front, e nomes só com os segmentos colidem (vários putPastorais).
 */
const nomeDaOperacao = (_operacao: unknown, rota: string, verbo: string): string => {
  const comParametros = rota.replace(
    /\$?\{(\w+)\}/g,
    (_, nome: string) => 'Por' + capitalizar(nome),
  );
  const partes = comParametros
    .split('/')
    .filter((p) => p && p !== 'api')
    .flatMap((p) => p.split('-'))
    .map(capitalizar);
  return verbo.toLowerCase() + partes.join('');
};

export default defineConfig({
  servio: {
    input: { target: 'http://localhost:8080/v3/api-docs' },
    output: {
      target: './src/api/generated/servio.ts',
      schemas: './src/api/generated/modelos',
      client: 'react-query',
      mode: 'single',
      override: {
        operationName: nomeDaOperacao,
        mutator: {
          path: './src/api/cliente.ts',
          name: 'clienteGerado',
        },
      },
    },
  },
});
