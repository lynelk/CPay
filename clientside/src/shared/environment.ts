import React from 'react';

export type CitoEnvironment = 'SANDBOX' | 'PRODUCTION';
export type EnvironmentPortal = 'admin' | 'merchant';

const eventName = 'cito-environment-change';

function key(portal: EnvironmentPortal): string {
  return `cito.environment.${portal}`;
}

export function readEnvironment(portal: EnvironmentPortal): CitoEnvironment {
  const fallback: CitoEnvironment = portal === 'admin' ? 'PRODUCTION' : 'SANDBOX';
  try {
    const value = localStorage.getItem(key(portal));
    return value === 'PRODUCTION' || value === 'SANDBOX' ? value : fallback;
  } catch {
    return fallback;
  }
}

export function environmentForCurrentPath(): CitoEnvironment {
  if (typeof window === 'undefined') return 'SANDBOX';
  return readEnvironment(window.location.pathname.startsWith('/bo/admin') ? 'admin' : 'merchant');
}

export function useEnvironment(portal: EnvironmentPortal): {
  environment: CitoEnvironment;
  setEnvironment: (environment: CitoEnvironment) => void;
} {
  const [environment, updateEnvironment] = React.useState<CitoEnvironment>(() => readEnvironment(portal));

  React.useEffect(() => {
    const sync = (): void => updateEnvironment(readEnvironment(portal));
    window.addEventListener('storage', sync);
    window.addEventListener(eventName, sync);
    return () => {
      window.removeEventListener('storage', sync);
      window.removeEventListener(eventName, sync);
    };
  }, [portal]);

  const setEnvironment = (next: CitoEnvironment): void => {
    localStorage.setItem(key(portal), next);
    window.dispatchEvent(new Event(eventName));
    updateEnvironment(next);
  };

  return { environment, setEnvironment };
}
