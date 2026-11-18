import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite'

// The browser talks to the Vite dev server only. /api is forwarded to the backend, so the
// frontend and the API share one origin (no CORS) and the refresh cookie is a first-party cookie.
// The backend must list http://localhost:5173 in AUTH_ALLOWED_ORIGINS (origin check, see README).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});