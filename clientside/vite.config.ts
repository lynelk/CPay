/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

const PROXY_TARGET = 'http://localhost:8081';
const proxied = [
  '/api',
  '/auth',
  '/admins',
  '/merchants',
  '/settings',
  '/audittrail',
  '/transactions',
  '/actuator',
];

export default defineConfig({
  plugins: [
    tailwindcss(), // Tailwind v4 Vite plugin (replaces PostCSS)
    react(), // @vitejs/plugin-react v6 (Vite 8 / OXC)
  ],
  server: {
    port: 3000,
    open: true,
    proxy: Object.fromEntries(
      proxied.map((p) => [p, { target: PROXY_TARGET, changeOrigin: true }]),
    ),
  },
  build: { outDir: 'build', sourcemap: false },
  define: {
    // Legacy fallback for any code still reading the CRA-style var during the
    // incremental migration. New code uses import.meta.env.VITE_API_BASE.
    'process.env.REACT_APP_API_BASE': JSON.stringify(
      process.env.REACT_APP_API_BASE || process.env.VITE_API_BASE || '',
    ),
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./vitest.setup.ts'],
    css: false,
    include: ['src/**/*.{test,spec}.{js,jsx,ts,tsx}'],
  },
});
