import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [
    tailwindcss(),   // Tailwind v4 Vite plugin (replaces PostCSS)
    react(),         // @vitejs/plugin-react v6 handles .jsx (Vite 8 OXC)
  ],
  server: {
    port: 3000,
    open: true,
    proxy: {
      "/api":          { target: "http://localhost:8081", changeOrigin: true },
      "/auth":         { target: "http://localhost:8081", changeOrigin: true },
      "/admins":       { target: "http://localhost:8081", changeOrigin: true },
      "/merchants":    { target: "http://localhost:8081", changeOrigin: true },
      "/settings":     { target: "http://localhost:8081", changeOrigin: true },
      "/audittrail":   { target: "http://localhost:8081", changeOrigin: true },
      "/transactions": { target: "http://localhost:8081", changeOrigin: true },
      "/actuator":     { target: "http://localhost:8081", changeOrigin: true },
    },
  },
  build: { outDir: "build", sourcemap: false },
  define: {
    "process.env.NODE_ENV": JSON.stringify("development"),
    "process.env.PUBLIC_URL": JSON.stringify(""),
  },
});
