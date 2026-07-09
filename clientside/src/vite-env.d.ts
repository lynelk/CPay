/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

// Allow importing legacy JS modules that have no type declarations.
declare module '*.svg';
declare module 'rc-easyui';
declare module 'react-localization';

// Minimal ambient for the build-time-replaced CRA fallback in shared/config.ts.
// Vite's `define` substitutes `process.env.REACT_APP_API_BASE` at build time.
declare const process: { env: Record<string, string | undefined> };
