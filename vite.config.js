import { defineConfig } from 'vite';
export default defineConfig({
  esbuild: { jsx: 'automatic' },
  server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy: {
    '/api': 'http://127.0.0.1:8787', '/oauth2': 'http://127.0.0.1:8787', '/bridge': 'http://127.0.0.1:8787'
  } },
  build: { outDir: 'dist' }
});
