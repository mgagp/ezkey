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
      '@': path.resolve(import.meta.dirname, './src'),
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
  build: {
    // Lazy routes already split pages; vendor + locale chunks keep the entry graph under Vite's
    // default 500 kB advisory (QA/Docker builds stay warning-free). Revisit if entry grows past limit.
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('/src/locales/')) {
            return 'locales';
          }
          if (!id.includes('node_modules')) {
            return undefined;
          }
          if (
            id.includes('/react/') ||
            id.includes('/react-dom/') ||
            id.includes('/react-router') ||
            id.includes('/scheduler/')
          ) {
            return 'vendor-react';
          }
          if (id.includes('@tanstack/react-query')) {
            return 'vendor-query';
          }
          if (id.includes('i18next') || id.includes('react-i18next')) {
            return 'vendor-i18n';
          }
          if (id.includes('lucide-react')) {
            return 'vendor-icons';
          }
          if (id.includes('zod') || id.includes('react-hook-form') || id.includes('@hookform')) {
            return 'vendor-forms';
          }
          return 'vendor-misc';
        },
      },
    },
  },
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
