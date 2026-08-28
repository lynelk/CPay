/**
 * Centralised runtime configuration.
 *
 * Production uses the same public origin for the UI and API. Requests are
 * sent through /api and the frontend nginx service forwards them to the CPay
 * backend over Railway's private network. VITE_API_BASE remains available for
 * local development and non-standard deployments.
 */
function readApiBase(): string {
  // Vite-native env var (preferred for explicit overrides).
  if (import.meta.env?.VITE_API_BASE) {
    return import.meta.env.VITE_API_BASE;
  }
  // Legacy fallback injected via vite.config `define` during migration.
  const legacy =
    typeof process !== 'undefined' && process.env
      ? (process.env as Record<string, string | undefined>).REACT_APP_API_BASE
      : undefined;
  return legacy ?? '/api';
}

export const API_BASE: string = readApiBase();

export function apiUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  const base = API_BASE.replace(/\/$/, '');
  const suffix = path.startsWith('/') ? path : `/${path}`;
  return `${base}${suffix}`;
}
