import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig(({ mode }) => ({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  define:
    mode === 'production'
      ? { 'import.meta.env.VITE_DEMO_MODE': '"false"' }
      : undefined,
  server: {
    // Proxy only the Admin API base path so app routes like /api-keys/48 are not
    // forwarded to the backend. Without this, requests to /api-keys/:id would
    // match '/api' and get a 404 from the API instead of the SPA index.html.
    proxy: {
      '/api/v1': {
        target: 'http://localhost:9080',
        changeOrigin: true,
      },
    },
  },
}))
