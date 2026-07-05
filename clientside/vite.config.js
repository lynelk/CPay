import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [
    react({
      // Handle JSX in .js files (CRA project uses .js not .jsx)
      include: ["**/*.js", "**/*.jsx"],
    }),
  ],

  // Tell esbuild to treat .js files as jsx during dep scanning
  optimizeDeps: {
    esbuildOptions: {
      loader: { ".js": "jsx" },
    },
  },

  server: {
    port: 3000,
    open: true,
    proxy: {
      "/api":           { target: "http://localhost:8081", changeOrigin: true },
      "/auth":          { target: "http://localhost:8081", changeOrigin: true },
      "/admins":        { target: "http://localhost:8081", changeOrigin: true },
      "/merchants":     { target: "http://localhost:8081", changeOrigin: true },
      "/settings":      { target: "http://localhost:8081", changeOrigin: true },
      "/audittrail":    { target: "http://localhost:8081", changeOrigin: true },
      "/transactions":  { target: "http://localhost:8081", changeOrigin: true },
      "/actuator":      { target: "http://localhost:8081", changeOrigin: true },
    },
  },

  build: {
    outDir: "build",
    sourcemap: false,
    // Same esbuild options for production build
    commonjsOptions: { transformMixedEsModules: true },
  },

  define: {
    "process.env.NODE_ENV": JSON.stringify("development"),
    "process.env.PUBLIC_URL": JSON.stringify(""),
  },

  // esbuild transform for .js files
  esbuild: {
    loader: "jsx",
    include: /src\/.*\.js$/,
    exclude: [],
  },
});
