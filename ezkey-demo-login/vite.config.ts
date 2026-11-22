import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// In development, Vite serves the app on 5173 and forwards /api to the Node back end (8082).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: { outDir: 'dist/web', emptyOutDir: true },
  server: {
    port: 5173,
    proxy: { '/api': 'http://localhost:8082', '/healthz': 'http://localhost:8082' },
  },
});
