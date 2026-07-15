/**
 * Centralised runtime configuration.
 *
 * Replaces the Create React App `process.env.REACT_APP_*` convention with
 * Vite's `import.meta.env.VITE_*`. A legacy fallback is kept so any code that
 * still reads the old value during the incremental migration keeps working.
 */
function readApiBase(): string {
  // Vite-native env var (preferred).
  if (import.meta.env?.VITE_API_BASE) {
    return import.meta.env.VITE_API_BASE;
  }
  // Legacy fallback injected via vite.config `define` during migration.
  const legacy =
    typeof process !== 'undefined' && process.env
      ? (process.env as Record<string, string | undefined>).REACT_APP_API_BASE
      : undefined;
  return legacy ?? '';
}

export const API_BASE: string = readApiBase();

export function apiUrl(path: string): string {
  return `${API_BASE}${path}`;
}
