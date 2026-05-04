/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'

export default defineConfig(({ mode }) => {
  /** Same demo stripping as production; `cloudflare` mode uses `.env.cloudflare` for API URL. */
  const productionLike = mode === 'production' || mode === 'cloudflare'
  return {
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
    // Avoid two React copies (breaks context) when dependencies resolve differently per chunk.
    dedupe: ['react', 'react-dom'],
  },
  // Dev: bind these to one pre-bundled graph so lazy routes + codegen refreshes rarely split React.
  optimizeDeps: {
    include: [
      'react',
      'react-dom',
      'react/jsx-runtime',
      'react-router',
      'react-router-dom',
      '@tanstack/react-query',
    ],
  },
  define: productionLike
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
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts', 'src/**/*.test.tsx'],
  },
  }
})
