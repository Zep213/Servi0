import { defineConfig, minimal2023Preset as preset } from '@vite-pwa/assets-generator/config';

/** Ícones do app gerados a partir de um SVG simples (cruz neutra). Rodar com `npm run gen:pwa-icons`. */
export default defineConfig({
  preset,
  images: ['public/icone.svg'],
});
