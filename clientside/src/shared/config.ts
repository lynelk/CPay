/**
 * Centralised runtime configuration.
 *
 * Production uses the same public origin for the UI and API. Browser-internal
 * controller calls are namespaced under /api/ui and nginx removes that UI-only
 * prefix before forwarding to Spring. Public CPay routes such as /api/v1 and
 * /api/v2 therefore keep their existing contract unchanged.
 *
 * VITE_API_BASE remains available for local development and non-standard
 * deployments.
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
  // vite.config keeps the legacy symbol defined as an empty string when no
  // override is supplied. Treat that empty value as "not configured" so the
  // production same-origin namespace remains the default in tests and builds.
  return legacy || '/api/ui';
}

export const API_BASE: string = readApiBase();

export function apiUrl(path: string): string {
  if (/^https?:\/\//i.test(path)) {
    return path;
  }

  const base = API_BASE.replace(/\/$/, '');
  const suffix = path.startsWith('/') ? path : `/${path}`;

  // Keep URL normalization idempotent. Some legacy call sites already pass a
  // value through apiUrl() before handing it to apiFetch(), which normalizes
  // again. Without this guard those calls became /api/ui/api/ui/... in
  // production and never matched the nginx UI proxy namespace correctly.
  if (suffix === base || suffix.startsWith(`${base}/`)) {
    return suffix;
  }

  // Public gateway contracts already live below /api on Spring and nginx
  // preserves that prefix. Only the browser-only legacy controller families
  // need the /api/ui namespace.
  if (suffix === '/api' || suffix.startsWith('/api/')) {
    return suffix;
  }

  return `${base}${suffix}`;
}
