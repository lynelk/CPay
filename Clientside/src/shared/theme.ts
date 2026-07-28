/**
 * Theme controller for the iOS design system.
 *
 * The design tokens in ios.css / ios-system.css already respond to
 * `prefers-color-scheme`. This adds an explicit, persisted override via a
 * `data-theme` attribute on <html> so users can force light/dark from the
 * top bar. `system` clears the override and falls back to the OS setting.
 */
export type ThemePreference = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'cpay-theme';

export function getStoredTheme(): ThemePreference {
  try {
    const value = localStorage.getItem(STORAGE_KEY);
    if (value === 'light' || value === 'dark' || value === 'system') {
      return value;
    }
  } catch {
    /* ignore storage errors (private mode, etc.) */
  }
  return 'system';
}

export function applyTheme(pref: ThemePreference): void {
  const root = document.documentElement;
  if (pref === 'system') {
    root.removeAttribute('data-theme');
  } else {
    root.setAttribute('data-theme', pref);
  }
}

export function setTheme(pref: ThemePreference): void {
  try {
    localStorage.setItem(STORAGE_KEY, pref);
  } catch {
    /* ignore */
  }
  applyTheme(pref);
}

/** Resolve what is actually being shown right now (system -> OS query). */
export function resolvedTheme(pref: ThemePreference = getStoredTheme()): 'light' | 'dark' {
  if (pref !== 'system') return pref;
  const prefersDark =
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches;
  return prefersDark ? 'dark' : 'light';
}

/** Cycle light -> dark -> system for a simple toggle button. */
export function nextTheme(pref: ThemePreference): ThemePreference {
  return pref === 'light' ? 'dark' : pref === 'dark' ? 'system' : 'light';
}

export function initTheme(): void {
  applyTheme(getStoredTheme());
}
