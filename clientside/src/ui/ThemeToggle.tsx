import React from 'react';
import { getStoredTheme, nextTheme, setTheme, type ThemePreference } from '../shared/theme';
import { SunIcon, MoonIcon, AutoThemeIcon } from './Icons';

const ICON: Record<ThemePreference, React.ReactElement> = {
  light: <SunIcon size={18} />,
  dark: <MoonIcon size={18} />,
  system: <AutoThemeIcon size={18} />,
};
const NEXT_LABEL: Record<ThemePreference, string> = {
  light: 'Switch to dark theme',
  dark: 'Switch to system theme',
  system: 'Switch to light theme',
};

/** Top-bar button cycling light → dark → system. */
export function ThemeToggle(): React.ReactElement {
  const [pref, setPref] = React.useState<ThemePreference>(getStoredTheme);
  function cycle() {
    const next = nextTheme(pref);
    setTheme(next);
    setPref(next);
  }
  return (
    <button
      type="button"
      className="ios-icon-btn"
      onClick={cycle}
      aria-label={NEXT_LABEL[pref]}
      title={`Theme: ${pref}`}
    >
      {ICON[pref]}
    </button>
  );
}
